package co.ledger.wallet.daemon.utils

object AkkaUtils {

  private val validSymbols = """-_.*$+:@&=,!~';"""

  private def isValidChar(c: Char): Boolean =
    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (validSymbols.indexOf(c) != -1)


  def validActorName(parts: String*) : String = parts.mkString(".").map ( c => if (isValidChar(c)) c else '_' )
}
