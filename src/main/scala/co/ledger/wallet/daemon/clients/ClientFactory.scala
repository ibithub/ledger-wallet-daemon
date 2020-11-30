package co.ledger.wallet.daemon.clients

import java.util.concurrent.Executors

import co.ledger.wallet.daemon.context.ApplicationContext
import co.ledger.wallet.daemon.libledger_core.async.{LedgerCoreExecutionContext, ScalaThreadDispatcher}

import scala.concurrent.ExecutionContext

object ClientFactory {
  lazy val webSocketClient = new ScalaWebSocketClient
  lazy val httpCoreClient = new HttpCoreClientPool(LedgerCoreExecutionContext.httpPool, new ScalaHttpClientPool)

  lazy val threadDispatcher = new ScalaThreadDispatcher(ApplicationContext.libcoreEc)

  lazy val apiClient: ApiClient = new ApiClient()(
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool((r: Runnable) => new Thread(r, "api-client")))
  )
}
