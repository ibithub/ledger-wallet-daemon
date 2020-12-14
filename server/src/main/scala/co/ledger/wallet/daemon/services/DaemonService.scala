package co.ledger.wallet.daemon.services

import com.twitter.inject.Logging

trait DaemonService extends Logging

class LogMsgMaker(private val inner: StringBuilder = new StringBuilder()) {
  private val self = this

  def append(key: String, value: Any): LogMsgMaker = {
    self.inner.append(" ")
      .append(key)
      .append("=")
      .append(value)
    self
  }

  override def toString: String = {
    self.inner.toString()
  }
}

object LogMsgMaker {
  def newInstance(msg: String): LogMsgMaker = {
    val maker: LogMsgMaker = new LogMsgMaker()
    maker.inner.append(msg)
    maker
  }
}
