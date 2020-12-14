package co.ledger.wallet.daemon.utils

import java.net.URL

object NetUtils {
  val HTTP_DEFAULT_PORT: Int = 80
  val HTTPS_DEFAULT_PORT: Int = 443

  def urlToHost(url: URL): Host = Host(url.getHost, url.getProtocol, resolvePort(url))

  def resolvePort(url: URL): Int = url.getPort match {
    case port if port > 0 => port
    case _ => url.getProtocol match {
      case "https" => HTTPS_DEFAULT_PORT
      case _ => HTTP_DEFAULT_PORT
    }
  }

  case class Host(hostName: String, protocol: String, port: Int)

}
