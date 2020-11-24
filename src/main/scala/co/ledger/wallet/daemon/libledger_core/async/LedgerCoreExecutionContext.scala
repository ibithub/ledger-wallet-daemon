package co.ledger.wallet.daemon.libledger_core.async

import java.util.concurrent._
import java.util.{Timer, TimerTask}

import co.ledger.core
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
  private val serialExecutionContexts: Seq[LedgerCoreExecutionContext] = (0 until maxCoreSerialCtx).map(createSerialExecutionContext)

  def createSerialExecutionContext(idx: Int): LedgerCoreExecutionContext = {
    LedgerCoreExecutionContext(ExecutionContext.fromExecutorService(new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS,
      new LinkedBlockingDeque[Runnable](100000),
      new NamedPoolThreadFactory(s"Serial-CorePool-$idx"),
      new LogOnlyPolicy(s"Serial-CorePool-$idx"))))
  }

  def apply(ec: ExecutionContext): LedgerCoreExecutionContext = new LedgerCoreExecutionContext(ec)

  lazy private[this] val opPool: ExecutionContext =
    ExecutionContext.fromExecutorService(
      new ThreadPoolExecutor(16, 16, 60L, TimeUnit.SECONDS,
        new LinkedBlockingDeque[Runnable](100000),
        new NamedPoolThreadFactory("CPU-CorePool"),
        new LogOnlyPolicy("CPU-CorePool")))

  def operationPool: LedgerCoreExecutionContext = apply(opPool)

  def newSerialQueue(name: String): LedgerCoreExecutionContext = {
    serialExecutionContexts(Math.abs(name.hashCode) % maxCoreSerialCtx)
  }

  /*
  def newSerialQueue(prefix: String): LedgerCoreExecutionContext = apply(
    ExecutionContext.fromExecutorService(
      new ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable],
        new ThreadFactory {
          override def newThread(r: Runnable): Thread = new Thread(r, s"Serial-CorePool-$prefix")
        }
      )
    ))
*/
  def httpPool: ExecutionContext =
    ExecutionContext.fromExecutorService(new ThreadPoolExecutor(16, 16, 60L, TimeUnit.SECONDS,
      new LinkedBlockingDeque[Runnable](100000),
      new NamedPoolThreadFactory("HTTP-CorePool"),
      new LogOnlyPolicy("HTTP-CorePool")))


  // ExecutionContext.fromExecutor(Executors.newCachedThreadPool((r: Runnable) => new Thread(r, "libcore-http-client")))
}
