package co.ledger.wallet.daemon.clients

import java.util.concurrent.{Executors, LinkedBlockingDeque, ThreadPoolExecutor, TimeUnit}

import co.ledger.wallet.daemon.libledger_core.async.{LedgerCoreExecutionContext, LogOnlyPolicy, ScalaThreadDispatcher}
import com.twitter.concurrent.NamedPoolThreadFactory

import scala.concurrent.ExecutionContext

object ClientFactory {
  lazy val webSocketClient = new ScalaWebSocketClient
  lazy val httpCoreClient = new HttpCoreClientPool(LedgerCoreExecutionContext.httpPool, new ScalaHttpClientPool)

  lazy val threadDispatcher = new ScalaThreadDispatcher(
    ExecutionContext.fromExecutorService(new ThreadPoolExecutor(4, 4, 60L, TimeUnit.SECONDS,
      new LinkedBlockingDeque[Runnable](100000),
      new NamedPoolThreadFactory("ThreadDispatcher-CorePool"),
      new LogOnlyPolicy("ThreadDispatcher-CorePool")))
  )

  lazy val apiClient: ApiClient = new ApiClient()(
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool((r: Runnable) => new Thread(r, "api-client")))
  )
}
