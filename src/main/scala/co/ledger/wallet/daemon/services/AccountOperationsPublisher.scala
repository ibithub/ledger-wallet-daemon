package co.ledger.wallet.daemon.services


import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import cats.data.OptionT
import cats.implicits._
import co.ledger.core._
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.libledger_core.async.LedgerCoreExecutionContext
import co.ledger.wallet.daemon.models.Operations.OperationView
import co.ledger.wallet.daemon.models.PoolInfo
import co.ledger.wallet.daemon.services.AccountOperationsPublisher._
import co.ledger.wallet.daemon.utils.AkkaUtils
import co.ledger.wallet.daemon.utils.Utils._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future => ScalaFuture}

class AccountOperationsPublisher(daemonCache: DaemonCache, account: Account, wallet: Wallet, poolName: PoolName, publisher: Publisher) extends Actor with ActorLogging {

  private var numberOfReceivedOperations: Int = 0

  implicit val dispatcher: ExecutionContextExecutor = context.dispatcher
  val lookupDispatcher: ExecutionContextExecutor = context.system.dispatchers.lookup(SynchronizationDispatcher.configurationKey(SynchronizationDispatcher.LibcoreLookup))
  private val walletPoolDao = Await.result(daemonCache.getWalletPool(PoolInfo(poolName.name)), 30.seconds).map(_.walletPoolDao).get

  private val eventReceiver = new AccountOperationReceiver(self)
  private val accountInfo: String = s"$poolName/${wallet.getName}/${account.getIndex}"

  private val operationsCountSubscribers: scala.collection.mutable.Set[ActorRef] = scala.collection.mutable.Set.empty[ActorRef]

  override def preStart(): Unit = {
    log.info(s"Actor $accountInfo is starting, Actor name=${self.path.name}")
    listenOperationsEvents(account)
    super.preStart()
  }

  override def postStop(): Unit = {
    log.info(s"Actor $accountInfo is stopping, Actor name=${self.path.name}")
    stopListeningEvents(account)
    super.postStop()
  }

  override def receive: Receive = LoggingReceive {
    case SubscribeToOperationsCount(subscriber) => operationsCountSubscribers.add(subscriber)
    case s: SyncStatus if account.isInstanceOfEthereumLikeAccount =>
      publisher.publishAccount(account, wallet, poolName.name, s).flatMap(_ => {
        publisher.publishERC20Accounts(account, wallet, poolName.name, s)
      })
    case s: SyncStatus => publisher.publishAccount(account, wallet, poolName.name, s)
    case NewERC20OperationEvent(_, opId) =>
      updateOperationsCount()
      fetchErc20OperationView(opId).fold(log.warning(s"operation not found: $opId"))(op => publisher.publishERC20Operation(op, account, wallet, poolName.name))
    case NewOperationEvent(opId) =>
      updateOperationsCount()
      fetchOperationView(opId).fold(log.warning(s"operation not found: $opId"))(op => publisher.publishOperation(op, account, wallet, poolName.name))
    case DeletedOperationEvent(opId) => publisher.publishDeletedOperation(opId.uid, account, wallet, poolName.name)
    case PublishOperation(op) =>
      publisher.publishOperation(op, account, wallet, poolName.name)
  }

  private def listenOperationsEvents(account: Account): Unit = eventReceiver.listenEvents(account.getEventBus)

  private def stopListeningEvents(account: Account): Unit = eventReceiver.stopListeningEvents(account.getEventBus)

  private def fetchOperationView(id: OperationId): OptionT[ScalaFuture, OperationView] =
    OptionT(walletPoolDao.findOperationByUid(account, wallet, id.uid, 0, Int.MaxValue).asScala())

  private def fetchErc20OperationView(erc20op: OperationId): OptionT[ScalaFuture, OperationView] =
    OptionT(walletPoolDao.findERC20OperationByUid(account, wallet, erc20op.uid).asScala())


  private def updateOperationsCount(): Unit = {
    numberOfReceivedOperations += 1
    operationsCountSubscribers.foreach(_ ! OperationsCount(numberOfReceivedOperations))
  }
}

object AccountOperationsPublisher {

  case class PoolName(name: String) extends AnyVal

  case class OperationId(uid: String) extends AnyVal

  case class Erc20AccountUid(uid: String) extends AnyVal

  case class NewOperationEvent(uid: OperationId)

  case class NewERC20OperationEvent(accUid: Erc20AccountUid, uid: OperationId)

  case class DeletedOperationEvent(uid: OperationId)

  private case class PublishOperation(op: OperationView)

  case object SyncEnded

  case object SyncEndedWithFailure

  case class SubscribeToOperationsCount(subscriber: ActorRef)

  case class OperationsCount(count: Int) extends AnyVal

  def props(cache: DaemonCache, account: Account, wallet: Wallet, poolName: PoolName, publisher: Publisher): Props =
    Props(new AccountOperationsPublisher(cache, account, wallet, poolName, publisher))

  def name(account: Account, wallet: Wallet, poolName: PoolName): String = AkkaUtils.validActorName("operation-publisher", poolName.name, wallet.getName, account.getIndex.toString)

}

class AccountOperationReceiver(eventTarget: ActorRef) extends EventReceiver {

  override def onEvent(event: Event): Unit = event.getCode match {

    case EventCode.NEW_OPERATION =>
      val uid = event.getPayload.getString(Account.EV_NEW_OP_UID)
      eventTarget ! NewOperationEvent(OperationId(uid))

    case EventCode.NEW_ERC20_OPERATION =>
      val uid = event.getPayload.getString(Account.EV_NEW_OP_UID)
      val accountUid = event.getPayload.getString(ERC20LikeAccount.EV_NEW_OP_ERC20_ACCOUNT_UID)
      eventTarget ! NewERC20OperationEvent(Erc20AccountUid(accountUid), OperationId(uid))

    case EventCode.DELETED_OPERATION =>
      val uid = event.getPayload.getString(Account.EV_DELETED_OP_UID)
      eventTarget ! DeletedOperationEvent(OperationId(uid))

    case _ =>
  }

  def listenEvents(bus: EventBus)(implicit ec: ExecutionContext): Unit = bus.subscribe(LedgerCoreExecutionContext(ec), this)

  def stopListeningEvents(bus: EventBus): Unit = bus.unsubscribe(this)
}

