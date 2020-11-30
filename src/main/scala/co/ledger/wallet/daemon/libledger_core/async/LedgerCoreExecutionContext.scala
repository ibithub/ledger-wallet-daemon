package co.ledger.wallet.daemon.libledger_core.async

import java.util.concurrent._
import java.util.{Timer, TimerTask}

import co.ledger.core
import co.ledger.wallet.daemon.context.ApplicationContext
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.inject.Logging

import scala.concurrent.ExecutionContext

class LedgerCoreExecutionContext(ec: ExecutionContext) extends co.ledger.core.ExecutionContext {

  override def execute(runnable: core.Runnable): Unit = ec.execute(() => {
    runnable.run()
    runnable.destroy()
  })

  override def delay(runnable: core.Runnable, millis: Long): Unit = {
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = execute(runnable)
    }, millis)
  }
}

/**
  * A handler for rejected tasks that log only
  * WARNING this is for debug purpose
  */
class LogOnlyPolicy(label: String) extends RejectedExecutionHandler with Logging {
  override def rejectedExecution(r: Runnable, e: ThreadPoolExecutor): Unit = {
    logger.info(s"Task aborted !! ${e.getQueue.size()} scheduled tasks on $label")
  }
}

object LedgerCoreExecutionContext extends Logging {
  val maxCoreSerialCtx = 12 // TODO make it configurable
  private val serialExecutionContexts: Seq[LedgerCoreExecutionContext] = (0 until maxCoreSerialCtx).map(_ => createSerialExecutionContext())

  def createSerialExecutionContext(): LedgerCoreExecutionContext = {
    LedgerCoreExecutionContext(ExecutionContext.fromExecutor(
      Executors.newSingleThreadExecutor(new NamedPoolThreadFactory("libcore-serial"))))
  }

  def apply(ec: ExecutionContext): LedgerCoreExecutionContext = new LedgerCoreExecutionContext(ec)

  lazy private[this] val opPool: ExecutionContext = ApplicationContext.libcoreEc

  def operationPool: LedgerCoreExecutionContext = apply(opPool)

  def newSerialQueue(name: String): LedgerCoreExecutionContext = {
    serialExecutionContexts(Math.abs(name.hashCode) % maxCoreSerialCtx)
  }

  def httpPool: ExecutionContext = ApplicationContext.httpEc
}
