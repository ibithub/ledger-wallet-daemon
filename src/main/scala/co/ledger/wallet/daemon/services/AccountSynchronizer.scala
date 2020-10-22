package co.ledger.wallet.daemon.services

import java.util.Date
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import java.util.concurrent.{ConcurrentHashMap, Executors, Semaphore}

import cats.implicits._
import co.ledger.core._
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.exceptions.AccountNotFoundException
import co.ledger.wallet.daemon.libledger_core.async.LedgerCoreExecutionContext
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.Wallet._
import co.ledger.wallet.daemon.models.{AccountInfo, Operations, Pool, PoolInfo}
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.inject.Logging
import com.twitter.util.{Duration, ScheduledThreadPoolTimer, Timer}
import javax.inject.{Inject, Singleton}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}
import scala.util.{Success, Try}


/**
  * This module is responsible to maintain account updated
  * It's pluggable to external trigger
  */
@Singleton
class AccountSynchronizerManager @Inject()(daemonCache: DaemonCache, publisher: Publisher)
  extends DaemonService {

  // FIXME : ExecutionContext size
  implicit val synchronizationPool: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4 * Runtime.getRuntime.availableProcessors()))

  // the scheduler used to schedule all the operation in ASM
  val scheduler = new ScheduledThreadPoolTimer(
    poolSize = 1,
    threadFactory = new NamedPoolThreadFactory("AccountSynchronizer-Scheduler")
  )

  // When we start ASM, we register the existing accounts
  // We periodically try to register account just in case there is new account created
  lazy private val periodicRegisterAccount =
    scheduler.schedule(Duration.fromSeconds(DaemonConfiguration.Synchronization.syncAccountRegisterInterval))(registerAccounts)

  // the cache to track the AS
  private val registeredAccounts = new ConcurrentHashMap[AccountInfo, AccountSynchronizer]()

  // should be called after the instancialization of this class
  def start(): Unit = {
    registerAccounts
    periodicRegisterAccount
    info("Started account synchronizer manager")
  }

  // An external control to resync an account
  def resyncAccount(accountInfo: AccountInfo): Unit = {
    Option(registeredAccounts.get(accountInfo)).foreach(_.resync())
  }

  // An external control to sync an account
  def syncAccount(accountInfo: AccountInfo): Future[SynchronizationResult] = {
    Option(registeredAccounts.get(accountInfo)).fold({
      warn(s"Trying to sync an unregistered account. $accountInfo")
      Future.failed[SynchronizationResult](AccountNotFoundException(accountInfo.accountIndex))
    })(_.eventuallyStartSync())
  }

  def syncPool(poolInfo: PoolInfo): Future[Seq[SynchronizationResult]] =
    daemonCache.withWalletPool(poolInfo)(pool =>
      for {
        wallets <- pool.wallets
        syncResults <- wallets.toList.traverse { wallet =>
            wallet.accounts.flatMap(_.toList.traverse(account =>
              syncAccount(AccountInfo(account.getIndex, wallet.getName, pool.name, poolInfo.pubKey)))
            )
        }
      } yield syncResults.flatten
    )

  def syncAllRegisteredAccounts(): Future[Seq[SynchronizationResult]] =
    Future.sequence(registeredAccounts.asScala.map {
      case (_, accountSynchronizer) => accountSynchronizer.eventuallyStartSync()
    }.toSeq)

  def registerAccount(account: Account, wallet: Wallet, accountInfo: AccountInfo): Unit = this.synchronized {
    registeredAccounts.computeIfAbsent(accountInfo, (i: AccountInfo) => {
      info(s"registered account $i to account synchronizer manager")
      new AccountSynchronizer(account, wallet, poolName = i.poolName, scheduler, publisher)
    })
  }

  def unregisterAccount(accountInfo: AccountInfo): Future[Unit] = this.synchronized {
    registeredAccounts.asScala.remove(accountInfo).fold(
      Future.failed[Unit](AccountNotFoundException(accountInfo.accountIndex))
    )(as => {
      as.close(Duration.fromMinutes(3)).map(_ => info(s"AccountSynchronizer for account $accountInfo Closed"))
    })
  }

  def unregisterPool(pool: Pool, poolInfo: PoolInfo): Future[Unit] = {
    info(s"Unregister Pool $poolInfo")
    for {
      wallets <- pool.wallets
      walletsAccount <- Future.sequence(wallets.map(wallet => for {
        accounts <- wallet.accounts
      } yield (wallet, accounts)))
    } yield {
      walletsAccount.map { case (wallet, accounts) =>
        accounts.map(account => {
          val accountInfo = AccountInfo(
            pubKey = poolInfo.pubKey,
            walletName = wallet.getName,
            poolName = pool.name,
            accountIndex = account.getIndex)
          unregisterAccount(accountInfo)
        })
      }
    }
  }

  // return None if account info not found
  def getSyncStatus(accountInfo: AccountInfo): Option[SyncStatus] = {
    Option(registeredAccounts.get(accountInfo)).map(_.getSyncStatus)
  }

  // Maybe to be called periodically to discover new account
  private def registerAccounts: Future[Unit] = {
    for {
      users <- daemonCache.getUsers
      pools <- Future.sequence(users.map(u => u.pools().map(_.map(p => (u, p))))).map(_.flatten)
      wallets <- Future.sequence(pools.map { case (user, pool) => pool.wallets.map(_.map(w => (user, pool, w))) }).map(_.flatten)
      accounts <- Future.sequence(wallets.map { case (user, pool, wallet) =>
        for {
          accounts <- wallet.accounts
        } yield accounts.map(account => (user, pool, wallet, account))
      }).map(_.flatten)
    } yield {
      accounts.foreach {
        case (user, pool, wallet, account) =>
          val accountInfo = AccountInfo(
            pubKey = user.pubKey,
            walletName = wallet.getName,
            poolName = pool.name,
            accountIndex = account.getIndex
          )
          registerAccount(account, wallet, accountInfo)
      }
    }
  }

  def close(after: Duration): Unit = {
    periodicRegisterAccount.cancel()
    periodicRegisterAccount.close(after)
    val closableTasks = Future.sequence(registeredAccounts.asScala.map {
      case (accountInfo, accountSynchronizer) =>
        info(s"Closing AccountSynchronizer for account $accountInfo")
        accountSynchronizer.close(after).map(_ => info(s"AccountSynchronizer for account $accountInfo Closed"))
    })
    Await.result(closableTasks, after.inMilliseconds.millisecond)
  }
}

/**
  * AccountSynchronizer manages all synchronization task related to the account.
  * An account sync will be triggerred periodically.
  * An account can have following states:
  * Synced(blockHeight)                    external trigger
  * periodic trigger ^                |    ^                      |
  * |                v       \                   v
  * Syncing(fromHeight)       Resyncing(targetHeight, currentHeight)
  *
  * @param ec the execution context for the synchronization job
  */
class AccountSynchronizer(account: Account,
                          wallet: Wallet,
                          poolName: String,
                          scheduler: Timer,
                          publisher: Publisher)
                         (implicit ec: ExecutionContext) extends Logging {
  private val walletName = wallet.getName
  // The core of the state machine, any change to it should be this.synchronised
  private var syncStatus: SyncStatus = Synced(0)
  private val syncFuture: AtomicReference[Future[SynchronizationResult]] = new AtomicReference(
    Future.successful(SynchronizationResult.apply(account.getIndex, walletName, poolName, syncResult = false)))
  // A counter to count the new operation event that is received.
  // Used to work with resync to indicate the resync progress
  private val opCounter: AtomicLong = new AtomicLong()

  // When the account is syncing, we received a resync request, we will
  // put it in queue. There is a synchronizer reading the queue periodically
  // to start the resync
  private val resyncLatch = {
    val s = new Semaphore(1)
    s.drainPermits()
    s
  }

  private val OP_ID_EVENT_KEY = "EV_NEW_OP_UID"

  private val eventReceiver = new EventReceiver {
    override def onEvent(event: Event): Unit = {
      event.getCode match {
        case EventCode.NEW_OPERATION =>
          opCounter.incrementAndGet()
          val uid = event.getPayload.getString(OP_ID_EVENT_KEY)
          account.operation(uid, 1).flatMap {
            case Some(op) => Operations.getView(op, wallet, account).map(Some.apply)
            case None => Future.successful(None)
          }.foreach {
            case Some(operationView) =>
                publisher.publishOperation(operationView, account, wallet, poolName)
                publisher.publishERC20Operation(operationView, account, wallet, poolName)
            case _ =>
          }
        case EventCode.DELETED_OPERATION =>
          val uid = event.getPayload().getString(Account.EV_DELETED_OP_UID)
          Await.result(
            publisher.publishDeletedOperation(uid, account, wallet, poolName),
            3.seconds
          )
        case _ =>
      }
    }
  }


  account.getEventBus.subscribe(LedgerCoreExecutionContext(ec), eventReceiver)

  // Periodically try to trigger sync. the sync will be triggered when status is Synced
  val periodicSyncTask = scheduler.schedule(Duration.fromSeconds(DaemonConfiguration.Synchronization.syncInterval)) {
    eventuallyStartSync()
  }

  // Periodically try to update the current height in resync status.
  // do nothing if the status is not Resyncing
  private val periodicResyncStatusCheckTask = scheduler.schedule(Duration.fromSeconds(DaemonConfiguration.Synchronization.syncStatusCheckInterval)) {
    periodicUpdateStatus()
  }
  // Periodically try to resync. It's competing with periodic sync.
  // The resync will be triggered when status is Synced and there is a resync latch
  private val periodicResyncCheckTask = scheduler.schedule(Duration.fromSeconds(DaemonConfiguration.Synchronization.resyncCheckInterval)) {
    tryResyncAccount()
  }

  def getSyncStatus: SyncStatus = this.synchronized(syncStatus.copy)

  // A external control for account resync
  // the resync will be queued if status is not Resyncing
  // Won't guarantee the resync will be triggered right away, the resync request
  // will be processed by a periodic check
  def resync(): Unit = this.synchronized {
    syncStatus match {
      case Resyncing(_, _) =>
      case _ =>
        info(s"RESYNC : resync task queued for $accountInfo")
        resyncLatch.release()
    }
  }

  /**
    * @param awaitOngoingSyncTimeout as ongoing sync cannot be canceled
    */
  def close(awaitOngoingSyncTimeout: Duration): Future[Unit] = {
    periodicResyncCheckTask.cancel()
    periodicResyncStatusCheckTask.cancel()
    periodicSyncTask.cancel()
    account.getEventBus.unsubscribe(eventReceiver)
    Future(Try(Await.result(syncFuture.get(), awaitOngoingSyncTimeout.inMilliseconds.millisecond))
      .fold(t => s"Failed to await for end of synchronization $accountInfo, status : $syncStatus due to error : $t",
        r => s"Successfully ended synchronization of account $accountInfo status : $syncStatus syncResult: $r"))
  }

  // This method is called periodically by `periodicSync` task
  // It can also be triggered by external command
  // Start a new sync or return the current synchronization future.
  def eventuallyStartSync(): Future[SynchronizationResult] = this.synchronized {
    syncStatus match {
      case Synced(_) | FailedToSync(_) => // do sync
        val lastHeight = lastBlockHeightSync
        syncStatus = Syncing(lastHeight, lastHeight)
        syncFuture.set(syncAccount())
      case _ => // do nothing
    }
    syncFuture.get()
  }


  // This method is called periodically by `periodicResyncStatusCheck` task
  private def periodicUpdateStatus() = this.synchronized {
    syncStatus match {
      case Resyncing(target, _) =>
        syncStatus = Resyncing(target, opCounter.get())
      case Syncing(fromHeight, _) =>
        val lastHeight = lastBlockHeightSync
        syncStatus = Syncing(fromHeight, lastHeight)
      case _ =>
    }
  }

  private def lastBlockHeightSync: Long = {
    val f: Future[Long] = account.getLastBlock().map(_.getHeight)
    Try(Await.result(f, 3.seconds)).getOrElse(-1)
  }

  // This method is called periodically by `periodicResyncCheck` task
  private def tryResyncAccount() = this.synchronized {
    if (resyncLatch.tryAcquire()) {
      info(s"RESYNC : try to resync account $accountInfo")
      syncStatus match {
        case Synced(_) | FailedToSync(_) => // do resync
          // await the future to be able to sync syncStatus change
          val targetOpCounts = Try(Await.result(account.operationCounts.map(_.values.sum), 10.seconds)).getOrElse(-1)
          opCounter.set(0)
          syncStatus = Resyncing(targetOpCounts, 0)
          info(s"RESYNC : resyncing $accountInfo")
          val syncTask = for {
            _ <- account.eraseDataSince(new Date(0))
            _ = info(s"Resync : erased all the operations of $accountInfo")
            syncTask <- syncAccount()
          } yield syncTask
          syncFuture.set(syncTask)
        case _ => // queue the resync
          info(s"RESYNC : the account $accountInfo is being syncing, postpone the resync")
          resyncLatch.release()
      }
    }
  }

  private def syncAccount(): Future[SynchronizationResult] = {
    onSynchronizationStart()
    account.sync(poolName, walletName)
      .andThen {
        case Success(value) if value.syncResult =>
          this.synchronized {
            syncStatus = Synced(lastBlockHeightSync)
            onSynchronizationEnds()
          }
        case _ =>
          this.synchronized {
            syncStatus = FailedToSync(s"SYNC : failed to sync account $accountInfo")
            onSynchronizationEnds()
          }
      }
  }

  private def onSynchronizationStart(): Unit = {
    info(s"SYNC : start syncing $accountInfo")
  }

  private def onSynchronizationEnds(): Unit = this.synchronized {
    info(s"SYNC : $accountInfo has been synced : $syncStatus")
//    val publish = for {
//      _ <- publisher.publishAccount(account, wallet, poolName, syncStatus)
//      _ <- if (account.isInstanceOfEthereumLikeAccount) publisher.publishERC20Accounts(account, wallet, poolName, syncStatus)
//           else Future.unit
//    } yield ()
//    Try(Await.result(publish, 10.seconds)) match {
//      case Failure(exception) => error(s"could not send account messages on $accountInfo with error ${exception.getMessage}")
//      case Success(_) => info(s"success in pushing account updates for $accountInfo")
//    }
  }


  private def accountInfo: String = {
    s"$poolName/$walletName/${account.getIndex}"
  }
}

sealed trait SyncStatus{
  def value: String
  def copy: SyncStatus
}

case class Synced(atHeight: Long) extends SyncStatus {
  @JsonProperty("value")
  def value: String = "synced"

  override def copy: SyncStatus = Synced(atHeight)
}

case class Syncing(fromHeight: Long, currentHeight: Long) extends SyncStatus {
  @JsonProperty("value")
  def value: String = "syncing"

  override def copy: SyncStatus = Syncing(fromHeight, currentHeight)
}

case class FailedToSync(reason: String) extends SyncStatus {
  @JsonProperty("value")
  def value: String = "failed"

  override def copy: SyncStatus = FailedToSync(reason)
}

/*
  * targetHeight is the height of the most recent operation of the account before the resync.
  * currentHeight is the height of the most recent operation of the account during resyncing.
  * they serve as a progress indicator
  */
case class Resyncing(
                      @JsonProperty("sync_status_target") targetOpCount: Long,
                      @JsonProperty("sync_status_current") currentOpCount: Long
                    ) extends SyncStatus {
  @JsonProperty("value")
  def value: String = "resyncing"

  override def copy: SyncStatus = Resyncing(targetOpCount, currentOpCount)
}
