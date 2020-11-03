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
import co.ledger.wallet.daemon.services.AccountOperationsPublisher._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class AccountOperationsPublisher(account: Account, wallet: Wallet, poolName: PoolName, publisher: Publisher) extends Actor with ActorLogging {

  implicit val ec: ExecutionContextExecutor = this.context.dispatcher
  private val eventReceiver = new AccountOperationReceiver(self)
  private val accountInfo: String = s"$poolName/${wallet.getName}/${account.getIndex}"

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

    case NewOperationEvent(opId) if account.isInstanceOfEthereumLikeAccount =>
      fetchOperation(opId).fold(log.warning(s"operation not found: $opId"))( op => {
        publisher.publishOperation(op._2, account, wallet, poolName.name)
        publishERC20Operations(op._1)
      })

    case NewOperationEvent(opId) => fetchOperation(opId).fold(log.warning(s"operation not found: $opId"))( op => publisher.publishOperation(op._2, account, wallet, poolName.name))
    case DeletedOperationEvent(opId) => publisher.publishDeletedOperation(opId.uid, account, wallet, poolName.name)
    case PublishOperation(op) => publisher.publishOperation(op, account, wallet, poolName.name)
  }

  private def listenOperationsEvents(account: Account): Unit = eventReceiver.listenEvents(account.getEventBus)

  private def stopListeningEvents(account: Account): Unit = eventReceiver.stopListeningEvents(account.getEventBus)

  private def fetchOperation(id: OperationId): OptionT[Future, (Operation, OperationView)] = for {
    op <- OptionT(account.operation(id.uid, 1))
    opView <- OptionT.liftF(Operations.getView(op, wallet, account))
  } yield (op, opView)


  private def publishERC20Operations(operation: Operation): Future[Unit] = {

    val erc20Accounts = account.asEthereumLikeAccount().getERC20Accounts.asScala
    val tx = operation.asEthereumLikeOperation().getTransaction
    val sender = tx.getSender.toEIP55
    val receiver = tx.getReceiver.toEIP55

    def getOperationsFromContractAddress(contractAddress: String) = erc20Accounts
      .find(_.getToken.getContractAddress.equalsIgnoreCase(contractAddress)).toSeq
      .flatMap(_.getOperations.asScala.filter(_.getHash.equalsIgnoreCase(tx.getHash)))

    val senderOps = getOperationsFromContractAddress(sender)
    val receiverOps = getOperationsFromContractAddress(receiver)

    Future.sequence(
      (senderOps ++ receiverOps) // Do the order have a meaning ? If not, TODO: retrieve operations in one way
        .map(erc20op => Operations.getErc20View(erc20op, operation, wallet, account)
          .map(publisher.publishERC20Operation(_, account, wallet, poolName.name)))
    ).map( opCounts => log.debug(s"${opCounts.length} operation(s) published from $accountInfo"))
  }


}

object AccountOperationsPublisher {

  case class PoolName(name: String) extends AnyVal

  case class OperationId(uid: String) extends AnyVal

  case class NewOperationEvent(uid: OperationId)

  case class DeletedOperationEvent(uid: OperationId)

  private case class PublishOperation(op: OperationView)

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

