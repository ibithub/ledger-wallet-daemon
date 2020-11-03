package co.ledger.wallet.daemon

import com.twitter.finatra.http.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTest

class ServerTest extends FeatureTest {
  override val server = new EmbeddedHttpServer(new ServerImpl)

  test("Health endpoint") {
    val response = server.httpGet(path = "/_health")
    response.status.code should be (200)
  }
}
