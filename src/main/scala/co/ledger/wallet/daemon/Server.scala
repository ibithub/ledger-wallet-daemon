package co.ledger.wallet.daemon

import co.ledger.wallet.daemon.controllers._
import co.ledger.wallet.daemon.filters._
import co.ledger.wallet.daemon.mappers._
import co.ledger.wallet.daemon.modules.{DaemonCacheModule, DaemonJacksonModule, PublisherModule}
import co.ledger.wallet.daemon.services.AccountSyncModule
import co.ledger.wallet.daemon.utils.NativeLibLoader
import com.google.inject.Module
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.Http
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.{AccessLoggingFilter, CommonFilters, LoggingMDCFilter, TraceIdMDCFilter}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.util.Duration

object Server extends ServerImpl

class ServerImpl extends HttpServer {

  override def jacksonModule: Module = DaemonJacksonModule

  override val modules: Seq[Module] = Seq(
    DaemonCacheModule,
    PublisherModule,
    AccountSyncModule
  )

  override protected def configureHttp(router: HttpRouter): Unit = {
    router
      .filter[AccessLoggingFilter[Request]]
      .filter[LoggingMDCFilter[Request, Response]]
      .filter[TraceIdMDCFilter[Request, Response]]
      .filter[CorrelationIdFilter[Request, Response]]
      .filter[CommonFilters]
      .add[PubKeyOrLWDFilter, AccountsController]
      .add[PubKeyOrLWDFilter, CurrenciesController]
      .add[StatusController]
      .add[PubKeyOrLWDFilter, WalletPoolsController]
      .add[PubKeyOrLWDFilter, WalletsController]
      .add[PubKeyOrLWDFilter, TransactionsController]
      // IMPORTANT: pay attention to the order, the latter mapper override the former mapper
      .exceptionMapper[DaemonExceptionMapper]
      .exceptionMapper[LibCoreExceptionMapper]
  }

  override protected def configureHttpServer(server: Http.Server): Http.Server = {
    server
      .withSession.maxIdleTime(Duration.fromSeconds(10))
      .withSession.maxLifeTime(Duration.fromMinutes(10))
  }

  override protected def warmup(): Unit = {
    super.warmup()
    NativeLibLoader.loadLibs()
  }
}
