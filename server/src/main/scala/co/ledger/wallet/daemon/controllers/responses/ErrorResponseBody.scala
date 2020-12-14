package co.ledger.wallet.daemon.controllers.responses

import com.fasterxml.jackson.annotation.JsonProperty

case class ErrorResponseBody(@JsonProperty("type") rc: ErrorCode, @JsonProperty("message") msg: Map[String, Any])
