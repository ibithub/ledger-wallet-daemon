package co.ledger.wallet.daemon.libledger_core.debug

import co.ledger.core.{ExecutionContext, LogPrinter}
import com.twitter.inject.Logging

class NoOpLogPrinter(ec: ExecutionContext, val isPrintEnabled: Boolean) extends LogPrinter with Logging {
  override def printError(message: String): Unit = if (isErrorEnabled) print("ERROR", message)
  override def printInfo(message: String): Unit = if (isInfoEnabled) print("INFO", message)
  override def printDebug(message: String): Unit = if (isDebugEnabled) print("DEBUG", message)
  override def printWarning(message: String): Unit = if (isWarnEnabled) print("WARN", message)
  override def printApdu(message: String): Unit = if (isDebugEnabled) print("APDU", message)
  override def printCriticalError(message: String): Unit = if (isErrorEnabled) print("CRITICAL", message)
  override def getContext: ExecutionContext = ec

  private def print(tag: String, message: String): Unit = if (isPrintEnabled) {
    // scalastyle:off
    println(s"From core [$tag]: $message")
    // scalastyle:on
  }
}
