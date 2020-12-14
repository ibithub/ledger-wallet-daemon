package co.ledger.wallet.daemon.libledger_core.async

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent._
import java.util.{Timer, TimerTask}

import co.ledger.core
import co.ledger.wallet.daemon.configurations.DaemonConfiguration

import scala.concurrent.ExecutionContext

class LedgerCoreExecutionContext(ec: ExecutionContext) extends co.ledger.core.ExecutionContext {

  override def execute(runnable: core.Runnable): Unit = ec.execute(() => runnable.run())

  override def delay(runnable: core.Runnable, millis: Long): Unit = {
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = execute(runnable)
    }, millis)
  }

}

object LedgerCoreExecutionContext {
  def apply(ec: ExecutionContext): LedgerCoreExecutionContext = new LedgerCoreExecutionContext(ec)

  lazy private[this] val opPool: ExecutionContext =
    ExecutionContext.fromExecutorService(
      Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors() * DaemonConfiguration.corePoolOpSizeFactor, new ThreadFactory {
        val cnt: AtomicInteger = new AtomicInteger

        override def newThread(r: Runnable): Thread = new Thread(r, s"CPU-CorePool-${cnt.incrementAndGet}")
      }))

  def operationPool: LedgerCoreExecutionContext = apply(opPool)

  def newSerialQueue(prefix: String): LedgerCoreExecutionContext = apply(
    ExecutionContext.fromExecutorService(
      new ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable],
        new ThreadFactory {
          override def newThread(r: Runnable): Thread = new Thread(r, s"Serial-CorePool-$prefix")
        }
      )
    ))

  def httpPool: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool((r: Runnable) => new Thread(r, "libcore-http-client")))
}
