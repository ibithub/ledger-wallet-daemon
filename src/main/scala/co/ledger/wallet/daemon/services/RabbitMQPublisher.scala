package co.ledger.wallet.daemon.services

import co.ledger.core.{Account, ERC20LikeAccount, ERC20LikeOperation, Operation, Wallet}
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.Currency._
import co.ledger.wallet.daemon.models.Operations
import com.rabbitmq.client.{BuiltinExchangeType, ConnectionFactory}
import com.twitter.finatra.json.FinatraObjectMapper
import com.twitter.inject.Logging
import javax.inject.Singleton

import scala.collection.mutable
import scala.concurrent.Future

@Singleton
class RabbitMQPublisher(rabbitMQUri: String) extends Logging with Publisher {
  private val conn = {
    val factory = new ConnectionFactory
    factory.setUri(rabbitMQUri)
    val conn = factory.newConnection
    info(s"RabbitMQ: connected to ${conn.getAddress.toString}")
    conn
  }
  private val chan = conn.createChannel()
  private val declaredExchanges = mutable.HashSet[String]()

  // Channel is not recommended to be used concurrently, hence synchronized
  private def publish(exchangeName: String, routingKeys: List[String], payload: Array[Byte]): Unit = synchronized {
    if (!declaredExchanges.contains(exchangeName)) {
      chan.exchangeDeclare(exchangeName, BuiltinExchangeType.TOPIC, true)
      declaredExchanges += exchangeName
    }
    chan.basicPublish(exchangeName, routingKeys.mkString("."), null, payload)
  }

  private val mapper = FinatraObjectMapper.create()

  def publishOperation(op: Operation, account: Account, wallet: Wallet, poolName: String): Future[Unit] = {
    operationPayload(op, account, wallet).map(payload =>
      publish(poolName, getTransactionRoutingKeys(op, account, wallet.getCurrency.getName, poolName), payload)
    )
  }

  def publishERC20Operation(erc20Operation: ERC20LikeOperation, op: Operation, account: Account, wallet: Wallet, poolName: String): Future[Unit] = {
    erc20OperationPayload(erc20Operation, op, account, wallet).map{ payload =>
      val routingKey = getERC20TransactionRoutingKeys(erc20Operation, account, wallet.getCurrency.getName, poolName)
      publish(poolName, routingKey, payload)
    }
  }

  def publishAccount(account: Account, wallet: Wallet, poolName: String, syncStatus: SyncStatus): Future[Unit] = {
    accountPayload(account, wallet, syncStatus).map { payload =>
      publish(poolName, getAccountRoutingKeys(account, wallet, poolName), payload)
    }
  }

  override def publishERC20Account(erc20Account: ERC20LikeAccount, account: Account, wallet: Wallet, syncStatus: SyncStatus, poolName: String): Future[Unit] = {
    erc20AccountPayload(erc20Account: ERC20LikeAccount, account: Account, wallet: Wallet, syncStatus: SyncStatus).map {
      payload => publish(poolName, getAccountRoutingKeys(account, wallet, poolName) ++ List("erc20"), payload)
    }
  }

  def publishDeletedOperation(uid: String, account: Account, wallet: Wallet, poolName: String): Future[Unit] = {
    Future{
      val routingKey = deleteOperationRoutingKeys(account, wallet, poolName)
      val payload = deleteOperationPayload(uid, account, wallet, poolName)
      publish(poolName, routingKey, payload)
    }
  }

  private def deleteOperationPayload(uid: String, account: Account, wallet: Wallet, poolName: String): Array[Byte] = {
    val map: Map[String, Any] = Map(
      "uid" -> uid,
      "account" -> account.getIndex(),
      "wallet" -> wallet.getName(),
      "poolName" -> poolName
    )
    mapper.writeValueAsBytes(map)
  }

  private def deleteOperationRoutingKeys(account: Account, wallet: Wallet, poolName: String): List[String] = {
    List(
      "transactions",
      poolName,
      wallet.getName(),
      account.getIndex().toString(),
      "delete"
    )
  }

  private def accountPayload(account: Account, wallet: Wallet, syncStatus: SyncStatus): Future[Array[Byte]] = this.synchronized {
    account.accountView(wallet.getName, wallet.getCurrency.currencyView, syncStatus).map {
      mapper.writeValueAsBytes(_)
    }
  }

  private def erc20AccountPayload(erc20Account: ERC20LikeAccount, account: Account, wallet: Wallet, syncStatus: SyncStatus): Future[Array[Byte]] = this.synchronized {
    account.erc20AccountView(erc20Account, wallet, syncStatus).map {
      mapper.writeValueAsBytes(_)
    }
  }

  private def getAccountRoutingKeys(account: Account, wallet: Wallet, poolName: String): List[String] = {
    List(
      "accounts",
      poolName,
      wallet.getCurrency.getName,
      account.getIndex.toString
    )
  }

  private def erc20OperationPayload(erc20Operation: ERC20LikeOperation, operation: Operation, account: Account, wallet: Wallet): Future[Array[Byte]] = {
    Operations.getErc20View(erc20Operation, operation, wallet, account).map{
      mapper.writeValueAsBytes(_)
    }
  }

  private def operationPayload(op: Operation, account: Account, wallet: Wallet): Future[Array[Byte]] = {
    Operations.getView(op, wallet, account).map {
      mapper.writeValueAsBytes(_)
    }
  }

  private def getERC20TransactionRoutingKeys(erc20Op: ERC20LikeOperation, account: Account, currencyName: String, poolName: String): List[String] = {
    List(
      "transactions",
      poolName,
      currencyName,
      account.getIndex.toString,
      "erc20",
      erc20Op.getOperationType.toString.toLowerCase()
    )
  }

  private def getTransactionRoutingKeys(op: Operation, account: Account, currencyName: String, poolName: String): List[String] = {
    List(
      "transactions",
      poolName,
      currencyName,
      account.getIndex.toString,
      op.getOperationType.toString.toLowerCase
    )
  }
}
