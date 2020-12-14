package co.ledger.wallet.daemon.filters

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.Future
import com.twitter.util.logging.Logging

class DeprecatedRouteFilter extends SimpleFilter[Request, Response] with Logging{
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    warn(s"Route ${request.path} has been DEPRECATED and will be removed in next versions. Please refer to the documentation.")
    service(request)
  }
}
