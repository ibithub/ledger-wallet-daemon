package co.ledger.wallet.daemon.controllers.responses

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.response.ResponseBuilder
import com.twitter.inject.Logging

object ResponseSerializer extends Logging {

  def serializeInternalError(request: Request, response: ResponseBuilder, caught: Throwable): Response = {
    val msg = Map(
      "response"->s"Internal server error occurred while processing the request. ${caught.getMessage}"
    )
    log(error(_, caught), ErrorCode.Internal_Error, request, msg)
    response.internalServerError.body(ErrorResponseBody(ErrorCode.Internal_Error, msg))
  }

  def serializeBadRequest(request: Request, msg: Map[String, Any], response: ResponseBuilder): Response = {
    log(info(_), ErrorCode.Bad_Request, request, msg)
    response.badRequest
      .body(ErrorResponseBody(ErrorCode.Bad_Request, msg))
  }

  def serializeNotFound(request: Request, msg: Map[String, Any], response: ResponseBuilder): Response = {
    log(info(_), ErrorCode.Not_Found, request, msg)
    response.notFound
      .body(ErrorResponseBody(ErrorCode.Not_Found, msg))
  }

  private[this] def log(msgLogger: String => Unit,
                        error: ErrorCode,
                        request: Request,
                        msg: Map[String, Any]): Unit = {
    msgLogger(s"$error - [${request.method.name}] ${request.path} - Message : $msg")
  }

  def serializeOk(obj: Any, request: Request, response: ResponseBuilder): Response = {
    val resp = response.ok(obj)
    if( logger.isDebugEnabled ) {
      debug(s"OK - [${request.method.name}] ${request.path} - Response : $resp")
    }
    resp
  }
}

