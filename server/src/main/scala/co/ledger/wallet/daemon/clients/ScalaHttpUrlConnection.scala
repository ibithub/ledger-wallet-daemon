package co.ledger.wallet.daemon.clients

import java.util

import co.ledger.core.HttpReadBodyResult

/**
  * Wrapper for already computed results
  * @param statusCode : Http return code
  * @param statusText : Status massage
  * @param headers : Map for header attributes
  * @param bodyResult : The body returned
  */
class ScalaHttpUrlConnection(statusCode: Int, statusText: String,
                             headers: util.HashMap[String, String],
                             bodyResult: HttpReadBodyResult) extends co.ledger.core.HttpUrlConnection() {

  override def getStatusCode: Int = statusCode

  override def getStatusText: String = statusText

  override def getHeaders: util.HashMap[String, String] = headers

  override def readBody(): HttpReadBodyResult = {
    bodyResult
  }
}
