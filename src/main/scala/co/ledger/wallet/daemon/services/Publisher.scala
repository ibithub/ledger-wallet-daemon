package co.ledger.wallet.daemon.services

import co.ledger.core._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.models.Operations.OperationView
import co.ledger.wallet.daemon.models.Pool
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.inject.Logging

import scala.collection.JavaConverters._
import scala.concurrent.Future


trait Publisher {
  def publishOperation(op: OperationView, account: Account, wallet: Wallet, poolName: String): Unit

  def publishERC20Operation(op: OperationView, account: Account, wallet: Wallet, poolName: String): Unit

  def publishAccount(pool: Pool, account: Account, wallet: Wallet, syncStatus: SyncStatus): Future[Unit]

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


sealed trait SyncStatus {
  def value: String
}

case class Synced(atHeight: Long) extends SyncStatus {
  @JsonProperty("value")
  def value: String = "synced"
}

case class Syncing(fromHeight: Long, currentHeight: Long) extends SyncStatus {
  @JsonProperty("value")
  def value: String = "syncing"
}

case class FailedToSync(reason: String) extends SyncStatus {
  @JsonProperty("value")
  def value: String = "failed"
}

/*
 * targetHeight is the height of the most recent operation of the account before the resync.
 * currentHeight is the height of the most recent operation of the account during resyncing.
 * they serve as a progress indicator
 */
case class Resyncing(@JsonProperty("sync_status_target") targetOpCount: Long,
                     @JsonProperty("synOperationCounterc_status_current") currentOpCount: Long)
  extends SyncStatus {
  @JsonProperty("value")
  def value: String = "resyncing"
}


// Dummy publisher that do nothing but log
class DummyPublisher extends Publisher with Logging {
  override def publishOperation(op: OperationView, account: Account, wallet: Wallet, poolName: String): Unit = {
    info(s"publish operation ${op.uid} of account:${account.getIndex}, wallet:${wallet.getName}, pool:$poolName")
  }

  override def publishERC20Operation(op: OperationView, account: Account, wallet: Wallet, poolName: String): Unit = {
    info(s"publish erc20 operation ${op.uid} of account:${account.getIndex}, wallet:${wallet.getName}, pool:$poolName")
  }

  override def publishAccount(pool: Pool, account: Account, wallet: Wallet, syncStatus: SyncStatus): Future[Unit] = {
    Future.successful(
      info(s"publish pool:${pool.name} ,account:${account.getIndex}, wallet:${wallet.getName}, syncStatus: $syncStatus")
    )
  }

  override def publishERC20Account(erc20Account: ERC20LikeAccount, account: Account, wallet: Wallet, syncStatus: SyncStatus, poolName: String): Future[Unit] = {
    Future.successful(
      info(s"publish erc20 balance token=${erc20Account.getToken} index=${account.getIndex} wallet=${wallet.getName} pool=${poolName}")
    )
  }

  override def publishDeletedOperation(uid: String, account: Account, wallet: Wallet, poolName: String): Future[Unit] = {
    Future.successful {
      info(s"delete operation $uid for account:${account.getIndex}, wallet:${wallet.getName}, pool:$poolName")
    }
  }
}
