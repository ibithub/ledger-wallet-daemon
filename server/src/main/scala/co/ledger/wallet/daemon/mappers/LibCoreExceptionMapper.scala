package co.ledger.wallet.daemon.mappers

import co.ledger.core.implicits.{LedgerCoreWrappedException, NotEnoughFundsException}
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.exceptions.ExceptionMapper
import com.twitter.finatra.http.response.ResponseBuilder
import javax.inject.{Inject, Singleton}

@Singleton
class LibCoreExceptionMapper @Inject()(response: ResponseBuilder)
  extends ExceptionMapper[LedgerCoreWrappedException] {
  override def toResponse(request: Request, throwable: LedgerCoreWrappedException): Response = throwable match {
    case _: NotEnoughFundsException =>
      ResponseSerializer.serializeBadRequest(request,
        Map("response" -> "Not enough funds"), response)
    case e =>
      ResponseSerializer.serializeInternalError(request, response, e)
  }
}

