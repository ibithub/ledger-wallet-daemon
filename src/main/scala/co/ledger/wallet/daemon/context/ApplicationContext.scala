package co.ledger.wallet.daemon.context

import java.util.concurrent.Executors

import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import com.twitter.concurrent.NamedPoolThreadFactory

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

object ApplicationContext {

  /**
    * Execution Context dedicated for waiting synchronization
    */
  implicit val IOPool: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool(new NamedPoolThreadFactory("walletdaemon-IO")))

  /**
    * Libcore thread dispatcher context
    */
  val libcoreEc: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2 * Runtime.getRuntime.availableProcessors(),
      new NamedPoolThreadFactory("libcore-dispatcher")))

  /**
    * Libcore http call ExcutionContext
    */
  val httpEc: ExecutionContext = ExecutionContext.fromExecutorService(
    Executors.newCachedThreadPool(new NamedPoolThreadFactory("libcore-http")))

  /**
    * Libcore http call ExcutionContext
    */
  val coreCpuEc: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(DaemonConfiguration.corePoolOpSizeFactor * Runtime.getRuntime.availableProcessors(),
      new NamedPoolThreadFactory("libcore-cpu")))

}
