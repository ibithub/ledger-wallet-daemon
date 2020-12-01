package co.ledger.wallet.daemon

import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.controllers._
import co.ledger.wallet.daemon.filters._
import co.ledger.wallet.daemon.mappers._
import co.ledger.wallet.daemon.modules.{DaemonCacheModule, DaemonJacksonModule, PublisherModule}
import co.ledger.wallet.daemon.services.AccountSyncModule
import co.ledger.wallet.daemon.utils.NativeLibLoader
import com.google.inject.Module
import com.timgroup.statsd.NonBlockingStatsDClientBuilder
import com.twitter.finagle.Http
import com.twitter.finagle.http.{Request, Response}
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
      .add[AccountsController]
      .add[CurrenciesController]
      .add[StatusController]
      .add[WalletPoolsController]
      .add[WalletsController]
      .add[TransactionsController]
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
    if (DaemonConfiguration.IS_DD_ENABLED) {
      logger.info(s"[DATADOG AGENT] Enabled : DD_HOST=${DaemonConfiguration.DD_HOST}, " +
        s"DD_PORT=${DaemonConfiguration.DD_PORT}, " +
        s"DD_TRACE_PREFIX=${DaemonConfiguration.DD_TRACE_PREFIX}, " +
        s"DD_TRACE_PORT=${DaemonConfiguration.DD_TRACE_PORT}")
      new NonBlockingStatsDClientBuilder().prefix(DaemonConfiguration.DD_TRACE_PREFIX).hostname(DaemonConfiguration.DD_HOST).port(DaemonConfiguration.DD_TRACE_PORT).build()
    } else {
      logger.info(s"[DATADOG AGENT] Disabled")
    }
  }
}
