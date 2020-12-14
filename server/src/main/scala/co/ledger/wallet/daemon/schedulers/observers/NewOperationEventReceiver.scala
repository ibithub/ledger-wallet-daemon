package co.ledger.wallet.daemon.schedulers.observers

import co.ledger.core._
import co.ledger.wallet.daemon.database.OperationCache
import com.twitter.inject.Logging

import scala.util.{Failure, Success, Try}

class NewOperationEventReceiver(private val poolId: Long, private val opsCache: OperationCache) extends EventReceiver with Logging {
  private val self = this

  override def onEvent(event: Event): Unit =
    if (event.getCode == EventCode.NEW_OPERATION) {
      Try(opsCache.updateOffset(
        poolId,
        event.getPayload.getString(Account.EV_NEW_OP_WALLET_NAME),
        event.getPayload.getInt(Account.EV_NEW_OP_ACCOUNT_INDEX))) match {
        case Success(_) => // Do nothing
        case Failure(e) => error("Failed to update offset with exception", e)
      }
    }

  private def canEqual(a: Any): Boolean = a.isInstanceOf[NewOperationEventReceiver]

  override def equals(that: Any): Boolean = that match {
    case that: NewOperationEventReceiver => that.canEqual(this) && self.hashCode() == that.hashCode()
    case _ => false
  }

  override def hashCode(): Int = {
    poolId.hashCode()
  }

  override def toString: String = s"NewOperationEventReceiver(pool_id: $poolId)"
}
