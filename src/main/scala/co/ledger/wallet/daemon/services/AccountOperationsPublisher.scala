package co.ledger.wallet.daemon.services

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import cats.data.OptionT
import cats.implicits._
import co.ledger.core._
import co.ledger.wallet.daemon.libledger_core.async.LedgerCoreExecutionContext
import co.ledger.wallet.daemon.models.Account.RichCoreAccount
import co.ledger.wallet.daemon.models.Operations
import co.ledger.wallet.daemon.models.Operations.OperationView
import co.ledger.wallet.daemon.models.coins.EthereumTransactionView
import co.ledger.wallet.daemon.services.AccountOperationsPublisher._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class AccountOperationsPublisher(account: Account, wallet: Wallet, poolName: PoolName, publisher: Publisher) extends Actor with ActorLogging {

  private var numberOfReceivedOperations: Int = 0

  implicit val ec: ExecutionContextExecutor = context.system.dispatchers.lookup("akka.wd-blocking-dispatcher")
  private val eventReceiver = new AccountOperationReceiver(self)
  private val accountInfo: String = s"$poolName/${wallet.getName}/${account.getIndex}"

  private val operationsCountSubscribers : scala.collection.mutable.Set[ActorRef] = scala.collection.mutable.Set.empty[ActorRef]

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

    case NewOperationEvent(opId) if account.isInstanceOfEthereumLikeAccount =>
      updateOpreationsCount()
      fetchOperationView(opId).fold(log.warning(s"operation not found: $opId"))(op => {
        publisher.publishOperation(op, account, wallet, poolName.name)
        publishERC20Operations(op)
      })

    case NewOperationEvent(opId) =>
      updateOpreationsCount()
      fetchOperationView(opId).fold(log.warning(s"operation not found: $opId"))(op => publisher.publishOperation(op, account, wallet, poolName.name))
    case DeletedOperationEvent(opId) => publisher.publishDeletedOperation(opId.uid, account, wallet, poolName.name)
    case PublishOperation(op) => publisher.publishOperation(op, account, wallet, poolName.name)
  }

  private def listenOperationsEvents(account: Account): Unit = eventReceiver.listenEvents(account.getEventBus)

  private def stopListeningEvents(account: Account): Unit = eventReceiver.stopListeningEvents(account.getEventBus)

  private def fetchOperationView(id: OperationId): OptionT[Future, OperationView] = OptionT(account.operationView(id.uid, 1, wallet))

  private def publishERC20Operations(operation: OperationView): Future[Unit] = {
    Future {
      val erc20Accounts = account.asEthereumLikeAccount().getERC20Accounts.asScala

      def getOperationsFromContractAddress(contractAddress: String, txHash: String) = erc20Accounts
        .find(_.getToken.getContractAddress.equalsIgnoreCase(contractAddress)).toSeq
        .flatMap(_.getOperations.asScala.filter(_.getHash.equalsIgnoreCase(txHash)))

      operation.transaction.map {
        case tx: EthereumTransactionView =>
          val senderOps = getOperationsFromContractAddress(tx.sender, tx.hash)
          val receiverOps = getOperationsFromContractAddress(tx.receiver, tx.hash)
          // Future.sequence(
          val operations = (senderOps ++ receiverOps) // Do the order have a meaning ? If not, TODO: retrieve operations in one way
            .map(erc20op => {
              val erc20View = Operations.getErc20View(erc20op, operation)
              publisher.publishERC20Operation(erc20View, account, wallet, poolName.name)
              erc20op
            })
          log.debug(s"${operations.length} operation(s) published from $accountInfo")
      }
    }
  }

  private def updateOpreationsCount() : Unit = {
    numberOfReceivedOperations += 1
    operationsCountSubscribers.foreach( _ ! OperationsCount(numberOfReceivedOperations) )
  }


}

object AccountOperationsPublisher {

  case class PoolName(name: String) extends AnyVal

  case class OperationId(uid: String) extends AnyVal

  case class NewOperationEvent(uid: OperationId)

  case class DeletedOperationEvent(uid: OperationId)

  private case class PublishOperation(op: OperationView)

  case object SyncEnded

  case object SyncEndedWithFailure

  case class SubscribeToOperationsCount(subscriber: ActorRef)

  case class OperationsCount(count: Int) extends AnyVal

  def props(account: Account, wallet: Wallet, poolName: PoolName, publisher: Publisher): Props = Props(new AccountOperationsPublisher(account, wallet, poolName, publisher))

  def name(account: Account, wallet: Wallet, poolName: PoolName): String = s"${poolName.name}.${wallet.getName}.${account.getIndex}"

}

class AccountOperationReceiver(eventTarget: ActorRef) extends EventReceiver {

  override def onEvent(event: Event): Unit = event.getCode match {

    case EventCode.NEW_OPERATION =>
      val uid = event.getPayload.getString(Account.EV_NEW_OP_UID)
      eventTarget ! NewOperationEvent(OperationId(uid))

    case EventCode.DELETED_OPERATION =>
      val uid = event.getPayload.getString(Account.EV_DELETED_OP_UID)
      eventTarget ! DeletedOperationEvent(OperationId(uid))

    case _ =>
  }

  def listenEvents(bus: EventBus)(implicit ec: ExecutionContext): Unit = bus.subscribe(LedgerCoreExecutionContext(ec), this)

  def stopListeningEvents(bus: EventBus): Unit = bus.unsubscribe(this)
}

