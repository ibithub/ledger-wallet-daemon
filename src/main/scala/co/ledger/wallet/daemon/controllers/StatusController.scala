package co.ledger.wallet.daemon.controllers


import co.ledger.core.LedgerCore
import co.ledger.wallet.daemon.BuildInfo
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.clients.{ApiClient, ClientFactory}
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.libledger_core.metrics.LibCoreMetrics
import co.ledger.wallet.daemon.libledger_core.metrics.LibCoreMetrics.AllocationMetric
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller

class StatusController extends Controller {

  import StatusController._

  /**
    * End point queries for the version of core library currently used.
    *
    */
  get("/_health") { request: Request =>
    info(s"GET _health $request")
    response.ok(Status(LedgerCore.getStringVersion))
  }

  get("/_version") { request: Request =>
    info(s"GET _version $request")
    response.ok(
      VersionResponse(
        BuildInfo.name, BuildInfo.version, BuildInfo.scalaVersion,
        BuildInfo.commitHash.getOrElse("unknown"), LedgerCore.getStringVersion, DaemonConfiguration.explorer))
  }

  get("/_metrics") { request: Request =>
    info(s"GET _metrics $request")
    response.ok(MetricsResponse(
      ClientFactory.httpCoreClient.poolCacheSize,
      ApiClient.fallbackServices.poolCacheSize,
      ApiClient.feeServices.poolCacheSize)
    )
  }

  get("/_metrics/libcore") { request: Request =>
    info(s"GET _metrics/libcore $request")
    import MDCPropagatingExecutionContext.Implicits.global
    LibCoreMetrics.fetch.map(LibCoreMetricsResponse.apply)
  }
}

object StatusController {

  case class Status(engine_version: String, status: String = "OK")

  case class VersionResponse(name: String, version: String, scalaVersion: String, commitHash: String, libcoreVersion: String, explorers: DaemonConfiguration.ExplorerConfig)

  case class MetricsResponse(coreHttpCachedPool: Long, feesHttpCachedPool: Long, fallbackHttpCachedPool: Long)

  case class LibCoreMetricsResponse(allocations: List[AllocationMetric])
}
