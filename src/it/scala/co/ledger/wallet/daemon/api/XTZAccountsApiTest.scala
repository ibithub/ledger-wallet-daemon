package co.ledger.wallet.daemon.api

// import co.ledger.wallet.daemon.clients.ClientFactory
// import co.ledger.wallet.daemon.controllers.TransactionsController.CreateXTZTransactionRequest
import co.ledger.wallet.daemon.models.FreshAddressView
// import co.ledger.wallet.daemon.models.coins.UnsignedRippleTransactionView
import co.ledger.wallet.daemon.services.OperationQueryParams
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.fasterxml.jackson.databind.JsonNode
import com.twitter.finagle.http.Status

// import scala.concurrent.Await
// import scala.concurrent.duration._

class XTZAccountsApiTest extends APIFeatureTest {

  val poolName = "xtz_test_pool"

  override def beforeAll(): Unit = {
    createPool(poolName)
  }

  override def afterAll(): Unit = {
    deletePool(poolName)
  }

  private val CORRECT_BODY_XTZ =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/1729'/0'",""" +
      """"pub_key": "03432A07E9AE9D557F160D9B1856F909E421B399E12673EEE0F4045F4F7BA151CF",""" +
      """"chain_code": "5D958E80B0373FA505B95C1DD175B0588205D1620C56F7247B028EBCB0FB5032"""" +
      """}""" +
      """]""" +
      """}"""

  test("Create XTZ account") {
    val walletName = "xtzWalletAccountCreation"
    assertWalletCreation(poolName, walletName, "tezos", Status.Ok)
    assertCreateAccount(CORRECT_BODY_XTZ, poolName, walletName, Status.Ok)
    val addresses = parse[Seq[FreshAddressView]](assertGetFreshAddresses(poolName, walletName, index = 0, Status.Ok))
    assert(addresses.nonEmpty)
    info(s"Here are addresses : $addresses")
    assertSyncAccount(poolName, walletName, 0)
    val operations = parse[Map[String, JsonNode]](assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(None, None, 1000, 0), Status.Ok))
    assert(operations.nonEmpty)
  }

  // test("Create XRP Transaction") {
  //   val walletName = "xrpWalletForCreateTX"
  //   assertWalletCreation(poolName, walletName, "ripple", Status.Ok)
  //   assertCreateAccount(CORRECT_BODY_XRP, poolName, walletName, Status.Ok)
  //   val addresses = parse[Seq[FreshAddressView]](assertGetFreshAddresses(poolName, walletName, index = 0, Status.Ok))
  //   assert(addresses.nonEmpty)
  //   info(s"Here are addresses : $addresses")
  //   assertSyncAccount(poolName, walletName, 0)
  //   val operations = parse[Map[String, JsonNode]](assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(None, None, 1000, 0), Status.Ok))
  //   assert(operations.nonEmpty)

  //   val networkFee = Await.result(ClientFactory.apiClient.getFeesRipple, 10.second)
  //   info(s"Network fee = $networkFee")
  //   // No fees, no feesLevel provided
  //   val sendToAddresses = List(addresses.head.address).map(add => XRPSendToRequest("10000", add))

  //   val txBadRequest = CreateXRPTransactionRequest(sendToAddresses, None, None, None, List.empty, None)
  //   val txBadRequestJson = server.mapper.objectMapper.writeValueAsString(txBadRequest)
  //   // Neither fees nor fees_level has been provided, expect failure due to MethodValidation check
  //   assertCreateTransaction(txBadRequestJson, poolName, walletName, 0, Status.BadRequest)

  //   // Check No fees amount provided with fee_level provided
  //   val txRequest = CreateXRPTransactionRequest(sendToAddresses, None, None, Some("FAST"), List.empty, None)
  //   val txRequestJson = server.mapper.objectMapper.writeValueAsString(txRequest)
  //   val transactionView = parse[UnsignedRippleTransactionView](assertCreateTransaction(txRequestJson, poolName, walletName, 0, Status.Ok))
  //   info(s"Here is transaction view : $transactionView")
  //   assert(transactionView.receiver == sendToAddresses.head.address)
  //   assert(transactionView.sender == sendToAddresses.head.address)

  //   // Check even if fees are higher or lower than networks one, they override network fees
  //   val highMaxFee: Int = 1234567
  //   val lowMaxFee: Int = 1
  //   val txRequestSlow = CreateXRPTransactionRequest(sendToAddresses, None,
  //     Some(String.valueOf(highMaxFee)), Some("SLOW"), List.empty, None)
  //   val txRequestJsonSlow = server.mapper.objectMapper.writeValueAsString(txRequestSlow)
  //   val transactionViewSlow = parse[UnsignedRippleTransactionView](assertCreateTransaction(txRequestJsonSlow, poolName, walletName, 0, Status.Ok))
  //   info(s"Here is transaction view : $transactionViewSlow")
  //   assert(BigInt(transactionViewSlow.fees) == BigInt(highMaxFee))

  //   val txRequestFast = CreateXRPTransactionRequest(sendToAddresses, None,
  //     Some(String.valueOf(lowMaxFee)), Some("FAST"), List.empty, None)
  //   val txRequestJsonFast = server.mapper.objectMapper.writeValueAsString(txRequestFast)
  //   val transactionViewFast = parse[UnsignedRippleTransactionView](assertCreateTransaction(txRequestJsonFast, poolName, walletName, 0, Status.Ok))
  //   info(s"Here is transaction view : $transactionViewFast")
  //   assert(BigInt(transactionViewFast.fees) == BigInt(lowMaxFee))

  // }


  // def transactionRequest(poolName: String, walletName: String, accountIdx: Int, xrpTransacRequest: CreateXRPTransactionRequest): String = {
  //   val txInfo = server.mapper.objectMapper.writeValueAsString(xrpTransacRequest)
  //   s"""{"pool_name:"$poolName","$walletName":"xrpWallet","account_index":$accountIdx, $txInfo"""
  // }

}

