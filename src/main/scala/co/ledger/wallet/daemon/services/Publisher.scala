package co.ledger.wallet.daemon.services

import co.ledger.core._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.models.Operations.OperationView
import com.twitter.inject.Logging

import scala.collection.JavaConverters._
import scala.concurrent.Future


trait Publisher {
  def publishOperation(op: OperationView, account: Account, wallet: Wallet, poolName: String): Unit

  def publishERC20Operation(op: OperationView, account: Account, wallet: Wallet, poolName: String): Unit

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

}

// Dummy publisher that do nothing but log
class DummyPublisher extends Publisher with Logging {
  override def publishOperation(op: OperationView, account: Account, wallet: Wallet, poolName: String): Unit = {
    info(s"publish operation ${op.uid} of account:${account.getIndex}, wallet:${wallet.getName}, pool:$poolName")
  }

  override def publishERC20Operation(op: OperationView, account: Account, wallet: Wallet, poolName: String): Unit = {
    info(s"publish erc20 operation ${op.uid} of account:${account.getIndex}, wallet:${wallet.getName}, pool:$poolName")
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
