package co.ledger.wallet.daemon

import java.nio.charset.StandardCharsets
import java.util.{Base64, Date}

import co.ledger.wallet.daemon.services.ECDSAService
import co.ledger.wallet.daemon.utils.{FixturesUtils, HexUtils}
import com.twitter.finatra.http.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTest
import org.bitcoinj.core.Sha256Hash

class ServerTest extends FeatureTest {
  override val server = new EmbeddedHttpServer(new ServerImpl)

  test("Authentication#Basic Authentication for demo users") {
    server.httpGet(path = "/_health", headers = basicAuthorisationHeader("admin", "password"))
  }

  test("Authentication#Authenticate with LWD whitelisted") {
    server.httpGet(path = "/_health", headers = lwdBasicAuthorisationHeader("whitelisted"))
  }

  test("Authentication#Authenticate with LWD whitelisted and valid timestamp (after now)") {
    server.httpGet(path = "/_health", headers = lwdBasicAuthorisationHeader("whitelisted", new Date(new Date().getTime + 10000)))
  }

  private def basicAuthorisationHeader(username: String, password: String) = Map(
    "authorization" -> s"Basic ${Base64.getEncoder.encodeToString(s"$username:$password".getBytes(StandardCharsets.UTF_8))}"
  )

  private def lwdBasicAuthorisationHeader(seedName: String, time: Date = new Date()) = {
    val ecdsa = server.injector.instance(classOf[ECDSAService])
    val privKey = Sha256Hash.hash(FixturesUtils.seed(seedName).getBytes)
    val pubKey = ecdsa.computePublicKey(privKey)
    val timestamp = time.getTime / 1000
    val message = Sha256Hash.hash(s"LWD: $timestamp\n".getBytes)
    val signed = ecdsa.sign(message, privKey)
    Map(
      "authorization" -> s"LWD ${Base64.getEncoder.encodeToString(s"${HexUtils.valueOf(pubKey)}:$timestamp:${HexUtils.valueOf(signed)}".getBytes(StandardCharsets.UTF_8))}"
    )
  }
}
