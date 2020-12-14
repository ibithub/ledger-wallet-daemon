package co.ledger.wallet.daemon.libledger_core.async

import java.util.concurrent.ConcurrentHashMap

import co.ledger.core.{Lock, ThreadDispatcher}

import scala.concurrent.ExecutionContext

class ScalaThreadDispatcher(mainContext: ExecutionContext) extends ThreadDispatcher {
  private val _mainContext = LedgerCoreExecutionContext(mainContext)
  // Keep track in order to keep a single pool instance
  private val _poolsSerial = new ConcurrentHashMap[String, co.ledger.core.ExecutionContext]()

  override def getSerialExecutionContext(name: String): co.ledger.core.ExecutionContext = {
    _poolsSerial.computeIfAbsent(name, name => LedgerCoreExecutionContext.newSerialQueue(name))
  }

  override def getThreadPoolExecutionContext(name: String): co.ledger.core.ExecutionContext = {
    LedgerCoreExecutionContext.operationPool
  }

  override def getMainExecutionContext: co.ledger.core.ExecutionContext = _mainContext

  // scalastyle:off
  override def newLock(): Lock = ???

  // scalastyle:on
}
