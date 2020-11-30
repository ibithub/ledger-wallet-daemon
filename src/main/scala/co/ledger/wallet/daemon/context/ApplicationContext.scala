package co.ledger.wallet.daemon.context

import java.util.concurrent.Executors

import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import com.twitter.concurrent.NamedPoolThreadFactory

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

object ApplicationContext {
  /**
    * Execution context dedicated to DB access waiting tasks
    */
  implicit val databaseEc: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4 * Runtime.getRuntime.availableProcessors(),
      new NamedPoolThreadFactory("walletdaemon-db-access")))

  /**
    * Execution Context dedicated for waiting synchronization
    */
  implicit val synchronizationPool: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool(new NamedPoolThreadFactory("walletdaemon-sync")))

  /**
    * Libcore thread dispatcher context
    */
  val libcoreEc: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2 * Runtime.getRuntime.availableProcessors(),
      new NamedPoolThreadFactory("libcore-dispatcher")))

  /**
    * Libcore http call ExcutionContext
    */
  val httpEc: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2 * Runtime.getRuntime.availableProcessors(),
      new NamedPoolThreadFactory("libcore-http")))

  /**
    * Libcore http call ExcutionContext
    */
  val coreCpuEc: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(DaemonConfiguration.corePoolOpSizeFactor * Runtime.getRuntime.availableProcessors(),
      new NamedPoolThreadFactory("libcore-cpu")))

}
