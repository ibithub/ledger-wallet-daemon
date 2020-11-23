package co.ledger.wallet.daemon.schedulers.observers

import co.ledger.core._
import co.ledger.wallet.daemon.database.OperationCache
import com.twitter.inject.Logging


class NewOperationEventReceiver(poolId: Long, opsCache: OperationCache) extends EventReceiver with Logging {
  private val self = this

  override def onEvent(event: Event): Unit = {
    if (poolId == opsCache.hashCode()) {
      event.isSticky
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
