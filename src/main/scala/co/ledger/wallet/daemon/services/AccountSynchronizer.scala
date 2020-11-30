package co.ledger.wallet.daemon.services

import java.util.Date
import java.util.concurrent.ConcurrentHashMap

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Timers}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import cats.data._
import cats.implicits._
import co.ledger.core._
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.exceptions.AccountNotFoundException
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.Wallet._
import co.ledger.wallet.daemon.models.{AccountInfo, Pool, PoolInfo}
import co.ledger.wallet.daemon.modules.PublisherModule
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import co.ledger.wallet.daemon.services.AccountOperationsPublisher.PoolName
import co.ledger.wallet.daemon.services.AccountSynchronizer._
import co.ledger.wallet.daemon.utils.AkkaUtils
import com.twitter.util.Timer
import javax.inject.{Inject, Singleton}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.Success
import scala.util.control.NonFatal

/**
  * This module is responsible to maintain account updated
  * It's pluggable to external trigger
  *
  * @param scheduler used to schedule all the operations in ASM
  */
@Singleton
class AccountSynchronizerManager @Inject()(daemonCache: DaemonCache, synchronizerFactory: AccountSyncModule.AccountSynchronizerFactory, scheduler: Timer)
  extends DaemonService {

  import co.ledger.wallet.daemon.context.ApplicationContext.synchronizationPool
  import com.twitter.util.Duration

  // When we start ASM, we register the existing accountsSuccess
  // We periodically try to register account just in case there is new account created
  lazy private val periodicRegisterAccount =
  scheduler.schedule(Duration.fromSeconds(DaemonConfiguration.Synchronization.syncAccountRegisterInterval))(registerAccounts)

  // the cache to track the AS
  private val registeredAccounts = new ConcurrentHashMap[AccountInfo, ActorRef]()

  // should be called after the instantiation of this class
  def start(): Future[Unit] = {
    registerAccounts.andThen {
      case Success(_) =>
        periodicRegisterAccount
        info("Started account synchronizer manager")
    }
  }


  // An external control to resync an account
  def resyncAccount(accountInfo: AccountInfo): Unit = {
    Option(registeredAccounts.get(accountInfo)).foreach(_ ! ReSync)
  }

  def syncAccount(accountInfo: AccountInfo): Future[SynchronizationResult] = {

    Option(registeredAccounts.get(accountInfo)).fold({
      warn(s"Trying to sync an unregistered account. $accountInfo")
      Future.failed[SynchronizationResult](
        AccountNotFoundException(accountInfo.accountIndex)
      )
    })(forceSync(_).map(synchronizationResult(accountInfo)))
  }

  private def forceSync(synchronizer: ActorRef) = {
    implicit val timeout: Timeout = Timeout(60 seconds)
    ask(synchronizer, ForceSynchronization).mapTo[SyncStatus]
  }

  def syncPool(poolInfo: PoolInfo): Future[Seq[SynchronizationResult]] =
    daemonCache.withWalletPool(poolInfo)(
      pool =>
        for {
          wallets <- pool.wallets
          syncResults <- wallets.toList.traverse { wallet =>
            wallet.accounts.flatMap(
              _.toList.traverse(
                account =>
                  syncAccount(
                    AccountInfo(
                      account.getIndex,
                      wallet.getName,
                      pool.name
                    )
                  )
              )
            )
          }
        } yield syncResults.flatten
    )

  def syncAllRegisteredAccounts(): Future[Seq[SynchronizationResult]] =
    Future.sequence(registeredAccounts.asScala.map {
      case (accountInfo, accountSynchronizer) => forceSync(accountSynchronizer).map(synchronizationResult(accountInfo))
    }.toSeq)

  def registerAccount(account: Account,
                      wallet: Wallet,
                      accountInfo: AccountInfo): Unit = this.synchronized {
    registeredAccounts.computeIfAbsent(
      accountInfo,
      (i: AccountInfo) => {
        info(s"Registered account $i to account synchronizer manager")
        synchronizerFactory(daemonCache, account, wallet, PoolName(i.poolName))
      }
    )
  }

  private def unregisterAccount(accountInfo: AccountInfo): Future[Unit] =
    this.synchronized {
      registeredAccounts.asScala
        .remove(accountInfo)
        .fold(
          Future
            .failed[Unit](AccountNotFoundException(accountInfo.accountIndex))
        )(as => {
          Future.successful(as ! PoisonPill) // A watcher would ensure the actor death
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
            walletName = wallet.getName,
            poolName = pool.name,
            accountIndex = account.getIndex)
          unregisterAccount(accountInfo)
        })
      }
    }
  }

  // return None if account info not found
  def getSyncStatus(accountInfo: AccountInfo): Future[Option[SyncStatus]] = {

    val status = for {
      synchronizer <- OptionT.fromOption[Future](Option(registeredAccounts.get(accountInfo)))
      status <- OptionT.liftF(ask(synchronizer, GetStatus)(Timeout(10 seconds)).mapTo[SyncStatus])
    } yield status

    status.value
  }

  // Maybe to be called periodically to discover new account
  private def registerAccounts: Future[Unit] = {
    for {
      pools <- daemonCache.getAllPools
      wallets <- Future.sequence(pools.map { pool => pool.wallets.map(_.map(w => (pool, w))) }).map(_.flatten)
      accounts <- Future.sequence(wallets.map { case (pool, wallet) =>
        for {
          accounts <- wallet.accounts
        } yield accounts.map(account => (pool, wallet, account))
      }).map(_.flatten)
    } yield {
      accounts.foreach {
        case (pool, wallet, account) =>
          val accountInfo = AccountInfo(
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
    registeredAccounts.asScala.foreach {
      case (accountInfo, accountSynchronizer) =>
        info(s"Closing AccountSynchronizer for account $accountInfo")
        accountSynchronizer ! PoisonPill
    }
  }

  private def synchronizationResult(accountInfo: AccountInfo)(syncStatus: SyncStatus): SynchronizationResult = {

    val syncResult = SynchronizationResult(accountInfo.accountIndex, accountInfo.walletName, accountInfo.poolName, _)

    syncStatus match {
      case Synced(_) => syncResult(true)
      case _ => syncResult(false)
    }
  }
}

/**
  * AccountSynchronizer manages all synchronization tasks related to the account.
  * An account sync will be triggerred periodically.
  * An account can have following states:
  * Synced(blockHeight)                    external trigger
  * periodic trigger ^                |    ^                      |
  * |                v       \                   v
  * Syncing(fromHeight)       Resyncing(targetHeight, currentHeight)
  *
  */
class AccountSynchronizer(cache: DaemonCache,
                          account: Account,
                          wallet: Wallet,
                          poolName: PoolName,
                          createPublisher: PublisherModule.OperationsPublisherFactory) extends Actor with Timers
  with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher
  private val walletName = wallet.getName
  private val accountInfo: String = s"${poolName.name}/$walletName/${account.getIndex}"

  private var operationPublisher: ActorRef = ActorRef.noSender

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"AccountSynchronizer for account $accountInfo Starting...")
    restartPublisher()
    self ! Init
  }

  override def postStop(): Unit = {
    log.info(s"AccountSynchronizer for account $accountInfo Closed")
  }

  override val receive: Receive = uninitialized()

  def uninitialized(): Receive = {
    case Init => lastAccountBlockHeight.pipeTo(self)
    case h: BlockHeight => context become idle(lastHeightSeen = h)
      self ! StartSynchronization
    case StartSynchronization =>
    case GetStatus | ReSync | ForceSynchronization => sender() ! Synced(0)

    case Failure(t) => log.error(t, s"An error occurred initializing synchro account $accountInfo")
  }

  private def idle(lastHeightSeen: BlockHeight): Receive = {
    case GetStatus => sender() ! Synced(lastHeightSeen.value)

    case ForceSynchronization => context become synchronizing(lastHeightSeen)
      sync().pipeTo(self)
      sender() ! updatedSyncingStatus(lastHeightSeen)

    case StartSynchronization => context become synchronizing(lastHeightSeen)
      sync().pipeTo(self)

    case ReSync => context become resyncing(lastHeightSeen, AccountOperationsPublisher.OperationsCount(0), SynchronizedOperationsCount(0))
      timers.cancel(StartSynchronization)
      restartPublisher()
      operationPublisher ! AccountOperationsPublisher.SubscribeToOperationsCount(self)
      wipeAllOperations()
        .pipeTo(self)

    case Failure(t) => log.error(t, s"An error occurred synchronizing account $accountInfo  at $lastHeightSeen")
      scheduleNextSync()
  }

  private def synchronizing(fromHeight: BlockHeight): Receive = {
    case StartSynchronization => // no one is expecting a response. Only the AccountSynchronizer itself can send this message
    case GetStatus | ForceSynchronization => updatedSyncingStatus(fromHeight).pipeTo(sender())
    case ReSync => timers.startSingleTimer(StartSynchronization, ReSync, 3 seconds)

    case SyncSuccess(height) => context become idle(lastHeightSeen = height)
      operationPublisher ! Synced(height.value)
      scheduleNextSync()

    case SyncFailure(reason) => context become idle(lastHeightSeen = fromHeight)
      operationPublisher ! FailedToSync(reason)
      scheduleNextSync()

    case h: BlockHeight => context become synchronizing(fromHeight = h)
      scheduleNextSync()

    case Failure(t) => context become idle(lastHeightSeen = fromHeight)
      log.error(t, s"#Sync : An error occurred synchronizing account $accountInfo  from $fromHeight")
      scheduleNextSync()
  }

  private def resyncing(heightBeforeResync: BlockHeight, current: AccountOperationsPublisher.OperationsCount, target: SynchronizedOperationsCount): Receive = {
    case StartSynchronization => // no one is expecting a response. Only the AccountSynchronizer itself can send this message
    case GetStatus | ForceSynchronization => sender() ! Resyncing(targetOpCount = target.value, currentOpCount = current.count)
    case ReSync =>

    case SyncSuccess(height) => context become idle(lastHeightSeen = height)
      operationPublisher ! Synced(height.value)
      scheduleNextSync()

    case SyncFailure(reason) => context become idle(lastHeightSeen = heightBeforeResync)
      operationPublisher ! FailedToSync(reason)
      scheduleNextSync()

    case c: AccountOperationsPublisher.OperationsCount => context become resyncing(heightBeforeResync, current = c, target)

    case c: SynchronizedOperationsCount => context become resyncing(heightBeforeResync, current, target = c)
      sync().pipeTo(self)

    case Failure(t) => context become idle(lastHeightSeen = heightBeforeResync)
      log.error(t, s"#Sync : An error occurred resyncing account $accountInfo (${current.count} / ${target.value} ops)")
      scheduleNextSync()
  }

  private def lastAccountBlockHeight: Future[BlockHeight] = account.getLastBlock()
    .map(h => BlockHeight(h.getHeight))
    .recover {
      case _: co.ledger.core.implicits.BlockNotFoundException => BlockHeight(0)
    }

  private def updatedSyncingStatus(atStartTime: BlockHeight) = lastAccountBlockHeight.map(lastHeight => Syncing(atStartTime.value, lastHeight.value))

  private def scheduleNextSync(): Unit = {
    val syncInterval = DaemonConfiguration.Synchronization.syncInterval.seconds
    timers.startSingleTimer(StartSynchronization, StartSynchronization, syncInterval)
  }

  private def restartPublisher(): Unit = {
    if (operationPublisher != ActorRef.noSender) {
      operationPublisher ! PoisonPill
    }
    operationPublisher = createPublisher(this.context, cache, account, wallet, poolName)
  }

  /**
    * @return the number of operations that had been synchronized by libcore before wiping
    */
  private def wipeAllOperations() = for {
    lastKnownOperationsCount <- account.operationCounts.map(_.values.sum)
    _ = log.info(s"#Sync : erased all the operations of account $accountInfo, last known op count $lastKnownOperationsCount")
    errorCode <- account.eraseDataSince(new Date(0))
  } yield {
    log.info(s"#Sync : account $accountInfo libcore wipe ended (errorCode : $errorCode ")
    SynchronizedOperationsCount(lastKnownOperationsCount)
  }


  private def sync(): Future[SyncResult] = {
    log.info(s"#Sync : start syncing $accountInfo")
    account
      .sync(poolName.name, walletName)
      .flatMap { result =>
        if (result.syncResult) {
          log.info(s"#Sync : $accountInfo has been synced : $result")
          lastAccountBlockHeight.map(SyncSuccess)
        } else {
          log.error(s"#Sync : $accountInfo has FAILED")
          Future.successful(SyncFailure(s"#Sync : Lib core failed to sync the account $accountInfo"))
        }
      }
      .recoverWith { case NonFatal(t) =>
        log.error(t, s"#Sync Failed to sync account: $accountInfo")
        Future.successful(SyncFailure(t.getMessage))
      }
  }
}

object AccountSynchronizer {

  /**
    * Wipe all the data's account from the database then restart a Sync
    *
    * @see co.ledger.wallet.daemon.services.SyncStatus
    */
  case object ReSync

  /**
    * Gives the status @SyncStatus of the synchronization of the account
    */
  object GetStatus

  /**
    * Start a new synchronization as soon as possible
    */
  case object ForceSynchronization

  private case object Init

  private case object StartSynchronization

  private sealed trait SyncResult

  private case class SyncSuccess(height: BlockHeight) extends SyncResult

  private case class SyncFailure(reason: String) extends SyncResult

  private case class BlockHeight(value: Long) extends AnyVal

  private case class SynchronizedOperationsCount(value: Int) extends AnyVal

  def name(account: Account, wallet: Wallet, poolName: PoolName): String = AkkaUtils.validActorName("account-synchronizer", poolName.name, wallet.getName, account.getIndex.toString)


}


sealed trait SynchronizationDispatcher

object SynchronizationDispatcher {

  object LibcoreLookup extends SynchronizationDispatcher

  object Synchronizer extends SynchronizationDispatcher

  object Publisher extends SynchronizationDispatcher

  type DispatcherKey = String

  def configurationKey(dispatcher: SynchronizationDispatcher): DispatcherKey = dispatcher match {
    case LibcoreLookup => "synchronization.libcore-lookup.dispatcher"
    case Synchronizer => "synchronization.synchronizer.dispatcher"
    case Publisher => "synchronization.publisher.dispatcher"
  }

}


