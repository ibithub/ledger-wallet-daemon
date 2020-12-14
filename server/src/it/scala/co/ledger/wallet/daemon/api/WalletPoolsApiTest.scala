package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.controllers.responses.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.models
import co.ledger.wallet.daemon.models.WalletPoolView
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.Status

class WalletPoolsApiTest extends APIFeatureTest {

  test("WalletPoolsApi#Create and list single pool") {
    createPool("my_pool")
    try {
      val pools = parse[List[models.WalletPoolView]](getPools())
      val pool = parse[models.WalletPoolView](getPool("my_pool"))
      assert(pools.contains(pool))
    } finally {
      deletePool("my_pool")
    }
  }

  test("WalletPoolsApi#Create pool with invalid name") {
    val wrongName = "my_pool; drop table my_pool,"
    try {
      createPool(wrongName, Status.BadRequest)
    } finally {
      // If status is Status.BadRequest, this do nothing
      deletePoolIfExists(wrongName)
    }
  }

  test("WalletPoolsApi#Create and list multiple pool") {
    createPool("this_pool")
    createPool("your_pool")
    val pools = parse[List[models.WalletPoolView]](getPools())
    List(WalletPoolView("your_pool", 0), WalletPoolView("this_pool", 0)).map { pool => assert(pools.contains(pool))}
    deletePool("your_pool")
    deletePool("this_pool")
    val pools2 = parse[List[models.WalletPoolView]](getPools())
    List(WalletPoolView("your_pool", 0), WalletPoolView("this_pool", 0)).map { pool => assert(!pools2.contains(pool))}
  }

  test("WalletPoolsApi#Get single pool") {
    val response = createPool("anotha_pool")
    assert(server.mapper.objectMapper.readValue[models.WalletPoolView](response.contentString) == WalletPoolView("anotha_pool", 0))
    val pool = parse[models.WalletPoolView](getPool("anotha_pool"))
    assert(pool == WalletPoolView("anotha_pool", 0))
    deletePool("anotha_pool")
  }

  test("WalletPoolsApi#Get and delete non-exist pool return bad request") {
    assert(
      server.mapper.objectMapper.readValue[ErrorResponseBody](getPool("not_exist_pool", Status.BadRequest).contentString).rc
        == ErrorCode.Bad_Request)
    assert(deletePool("another_not_exist_pool").contentString === "")
  }

  test("WalletPoolsApi#Create same pool twice return ok") {
    assert(
      server.mapper.objectMapper.readValue[models.WalletPoolView](createPool("same_pool").contentString)
        == WalletPoolView("same_pool", 0))
    assert(
      server.mapper.objectMapper.readValue[models.WalletPoolView](createPool("same_pool").contentString)
        == WalletPoolView("same_pool", 0))
    deletePool("same_pool")
  }

}
