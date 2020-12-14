package co.ledger.wallet.daemon.filters

import java.nio.charset.StandardCharsets
import java.util.Base64

import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.exceptions.UserNotFoundException
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import javax.inject.Inject
import co.ledger.wallet.daemon.utils.Utils._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import scala.concurrent.{Future => ScalaFuture}

class PubKeyOrLWDFilter @Inject() (daemonCache: DaemonCache) extends SimpleFilter[Request, Response] {
  private val PUBKEY_HEADER = "pubkey"

  private val LWD_AUTH_STRING_PATTERN = "([0-9a-fA-F]+):(.*):(.*)".r
  private val LWD_AUTHORIZATION_PREFIX: Int = 4
  private val LWD_AUTH_HEADER = "authorization"

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    request.headerMap.get(PUBKEY_HEADER).orElse {
      request.headerMap.get(LWD_AUTH_HEADER).filter(_ contains "LWD").map{ value =>
        val auth = new String(Base64.getDecoder.decode(value.substring(LWD_AUTHORIZATION_PREFIX).getBytes(StandardCharsets.UTF_8)))
        val LWD_AUTH_STRING_PATTERN(pubKey, _, _) = auth
        pubKey
      }
    } match {
      case Some(pubkey) =>
        daemonCache.getUser(pubkey).flatMap{
          case None if DaemonConfiguration.isWhiteListDisabled =>
            daemonCache.createUser(pubkey, 0).map(Some(_))
          case v => ScalaFuture.successful(v)
        }.map {
          case None =>
            throw UserNotFoundException(pubkey)
          case Some(user) =>
            AuthentifiedUserContext.setUser(request, user)
        } asTwitter() flatMap(_ => service(request))
      case None =>
        Future.value(Response(Status.Forbidden))
    }
  }
}
