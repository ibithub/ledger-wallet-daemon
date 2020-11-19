package co.ledger.wallet.daemon.mappers

import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.exceptions.ExceptionMapper
import com.twitter.finatra.http.response.ResponseBuilder
import javax.inject.{Inject, Singleton}

@Singleton
class CustomExceptionMapper @Inject()(response: ResponseBuilder)
  extends ExceptionMapper[RuntimeException] {
  override def toResponse(request: Request, throwable: RuntimeException): Response = throwable match {
    case e =>
      ResponseSerializer.serializeInternalError(request, response, e)
  }
}

