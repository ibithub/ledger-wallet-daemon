package co.ledger.wallet.daemon.schedulers.observers

import co.ledger.core.{Event, EventCode, EventReceiver}
import com.twitter.inject.Logging

import scala.concurrent.Promise

class SynchronizationEventReceiver(
                                    accountIndex: Int,
                                    walletName: String,
                                    poolName: String,
                                    promise: Promise[SynchronizationResult]) extends EventReceiver with Logging {

  override def onEvent(event: Event): Unit = {
    if (event.getCode == EventCode.SYNCHRONIZATION_SUCCEED ||
      event.getCode == EventCode.SYNCHRONIZATION_SUCCEED_ON_PREVIOUSLY_EMPTY_ACCOUNT) {
      promise.success(SynchronizationResult(accountIndex, walletName, poolName, syncResult = true))
    } else if (event.getCode == EventCode.SYNCHRONIZATION_FAILED) {
      promise.success(SynchronizationResult(accountIndex, walletName, poolName, syncResult = false))
    }
  }
}

case class SynchronizationResult(accountIndex: Int, walletName: String, poolName: String, syncResult: Boolean)
