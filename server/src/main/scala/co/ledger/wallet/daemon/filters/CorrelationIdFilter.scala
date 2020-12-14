package co.ledger.wallet.daemon.filters

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.Request
import com.twitter.util.Future
import org.slf4j.MDC

class CorrelationIdFilter[Req, Rep] extends SimpleFilter[Req, Rep] {

  override def apply(request: Req, service: Service[Req, Rep]): Future[Rep] = {
    val correlationID = request.asInstanceOf[Request].headerMap.getOrElse("X-Correlation-ID", "")
    MDC.put("correlationId", correlationID)
    service(request)
  }
}
