package co.ledger.wallet.daemon.services

import co.ledger.core.{Account, ERC20LikeOperation, Operation, Wallet}

import scala.concurrent.Future
import scala.collection.JavaConverters._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import com.twitter.inject.Logging

trait Publisher {
  def publishOperation(op: Operation, account: Account, wallet: Wallet, poolName: String): Future[Unit]

  def publishERC20Operation(erc20Operation: ERC20LikeOperation, op: Operation, account: Account, wallet: Wallet, poolName: String): Future[Unit]

  def publishAccount(account: Account, wallet: Wallet, poolName: String, syncStatus: SyncStatus): Future[Unit]

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
  override def publishOperation(op: Operation, account: Account, wallet: Wallet, poolName: String): Future[Unit] = {
    Future.successful(
      info(s"publish operation ${op.getUid} of account:${account.getIndex}, wallet:${wallet.getName}, pool:$poolName")
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
}