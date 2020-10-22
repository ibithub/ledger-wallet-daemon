package co.ledger.wallet.daemon.services

import co.ledger.core.{Account, ERC20LikeAccount, ERC20LikeOperation, Operation, Wallet}

import scala.concurrent.Future
import scala.collection.JavaConverters._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.models.Operations.OperationView
import com.twitter.inject.Logging


trait Publisher {
  def publishOperation(op: OperationView, account: Account, wallet: Wallet, poolName: String): Unit

  def publishERC20Operation(erc20Operation: ERC20LikeOperation, op: Operation, account: Account, wallet: Wallet, poolName: String): Future[Unit]

  def publishAccount(account: Account, wallet: Wallet, poolName: String, syncStatus: SyncStatus): Future[Unit]

  def publishERC20Account(erc20Account: ERC20LikeAccount, account: Account, wallet: Wallet, syncStatus: SyncStatus, poolName: String): Future[Unit]

  def publishERC20Accounts(account: Account, wallet: Wallet, poolName: String, syncStatus: SyncStatus): Future[Unit] = {
    val ethAccount = account.asEthereumLikeAccount()
    Future.sequence {
      ethAccount.getERC20Accounts.asScala.map {
        erc20Account => publishERC20Account(erc20Account, account, wallet, syncStatus, poolName)
      }
    }.map(_ => Unit)
  }

  def publishDeletedOperation(uid: String, account: Account, wallet: Wallet, poolName: String): Future[Unit]

  // Publish all the ERC20LikeOperations which the Operation could contain
  def publishERC20Operation(op: Operation, account: Account, wallet: Wallet, poolName: String): Future[Unit] = {
    if (account.isInstanceOfEthereumLikeAccount) {
      val erc20Accounts = account.asEthereumLikeAccount().getERC20Accounts.asScala
      val ethereumTransaction = op.asEthereumLikeOperation().getTransaction
      val senderAddress = ethereumTransaction.getSender.toEIP55
      val receiverAddress = ethereumTransaction.getReceiver.toEIP55
      Future.sequence(erc20Accounts.filter { erc20Account =>
        val contractAddress = erc20Account.getToken.getContractAddress
        contractAddress.equalsIgnoreCase(senderAddress) ||
          contractAddress.equalsIgnoreCase(receiverAddress)
      }.flatMap { erc20Account =>
        erc20Account.getOperations.asScala
          .filter(_.getHash.equalsIgnoreCase(ethereumTransaction.getHash))
          .map { erc20Operation =>
            publishERC20Operation(erc20Operation, op, account, wallet, poolName)
          }
      }).map(_ => Unit)
    } else {
      Future.unit
    }
  }
}

// Dummy publisher that do nothing but log
class DummyPublisher extends Publisher with Logging {
  override def publishOperation(op: OperationView, account: Account, wallet: Wallet, poolName: String): Unit = {
    Future.successful(
      info(s"publish operation ${op.uid} of account:${account.getIndex}, wallet:${wallet.getName}, pool:$poolName")
    )
  }

  override def publishERC20Operation(erc20Operation: ERC20LikeOperation, op: Operation, account: Account, wallet: Wallet, poolName: String): Future[Unit] = {
    Future.successful(
      info(s"publish erc20 operation ${erc20Operation.getHash} of account:${account.getIndex}, wallet:${wallet.getName}, pool:$poolName")
    )
  }

  override def publishAccount(account: Account, wallet: Wallet, poolName: String, syncStatus: SyncStatus): Future[Unit] = {
    Future.successful(
      info(s"publish account:${account.getIndex}, wallet:${wallet.getName}, pool:$poolName, syncStatus: $syncStatus")
    )
  }

  override def publishERC20Account(erc20Account: ERC20LikeAccount, account: Account, wallet: Wallet, syncStatus: SyncStatus, poolName: String): Future[Unit] = {
    Future.successful(
      info(s"publish erc20 balance token=${erc20Account.getToken} index=${account.getIndex} wallet=${wallet.getName} pool=${poolName}")
    )
  }

  override def publishDeletedOperation(uid: String, account: Account, wallet: Wallet, poolName: String): Future[Unit] = {
    Future.successful{
      info(s"delete operation $uid for account:${account.getIndex}, wallet:${wallet.getName}, pool:$poolName")
    }
  }
}
