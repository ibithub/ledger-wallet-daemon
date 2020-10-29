package co.ledger.wallet.daemon.api

import co.ledger.core._
import co.ledger.wallet.daemon.controllers.TransactionsController.CreateXTZTransactionRequest
import co.ledger.wallet.daemon.models.FreshAddressView
import co.ledger.wallet.daemon.models.Operations.OperationView
import co.ledger.wallet.daemon.models.coins.UnsignedTezosTransactionView
import co.ledger.wallet.daemon.services.{OperationQueryParams, SyncStatus}
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.fasterxml.jackson.databind.JsonNode
import com.twitter.finagle.http.Status
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.{times, verify, when}

import scala.concurrent.Future

class XTZAccountsApiTest extends APIFeatureTest {
  val poolName = "tez_test_pool"

  override def beforeAll(): Unit = {
    createPool(poolName)
    when(publisher.publishAccount(any[Account], any[Wallet], meq(poolName), any[SyncStatus])).thenReturn(Future.unit)
    // when(publisher.publishOperation(any[OperationView], any[Account], any[Wallet], meq(poolName))).thenReturn(Future.unit)
  }

  override def afterAll(): Unit = {
    deletePool(poolName)
  }

  private val CORRECT_BODY_XTZ =
    """{
      "account_index": 0,
      "derivations": [
        {
          "owner": "main",
          "path": "44'/1729'/0'",
          "pub_key": "0288FE6B557457B08D15519288E33FAE30D646992B6C10FF18E69E3134D2A10113",
          "chain_code": "F85FDD6DBA5E02A946E47D492218D8BF913BF33F30D9B9C04D2EE5093186D637"
        }
      ]
    }"""

  test("Create XTZ account") {
    val walletName = "xtzWalletAccountCreation"
    assertWalletCreation(poolName, walletName, "tezos", Status.Ok)
    assertCreateAccount(CORRECT_BODY_XTZ, poolName, walletName, Status.Ok)
    assertSyncPools(Status.Ok)
    val addresses = parse[Seq[FreshAddressView]](assertGetFreshAddresses(poolName, walletName, index = 0, Status.Ok))
    assert(addresses.nonEmpty)
    info(s"Here are addresses : $addresses")
    assertSyncAccount(poolName, walletName, 0)
    val operations = parse[Map[String, JsonNode]](assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(None, None, 1000, 0), Status.Ok))
    assert(operations.nonEmpty)
    // verify XTZ NRT integration
    val walletCaptor = ArgumentCaptor.forClass(classOf[Wallet])
    verify(publisher, times(1)).publishAccount(any[Account], walletCaptor.capture(), meq(poolName), any[SyncStatus])
    assert(walletCaptor.getValue.getWalletType == WalletType.TEZOS)
    val walletOperationCaptor = ArgumentCaptor.forClass(classOf[Wallet])
    val operationCaptor = ArgumentCaptor.forClass(classOf[OperationView])
    verify(publisher, times(1)).publishOperation(operationCaptor.capture(), any[Account], walletOperationCaptor.capture(), meq(poolName))
    assert(walletOperationCaptor.getValue.getWalletType == WalletType.TEZOS)
    assert(operationCaptor.getValue.opType == OperationType.RECEIVE)
    assert(operationCaptor.getValue.recipients.contains("tz2QKSvtCDHDFResmiUQfDsNTuYhM9nuhv1E"))
  }

  test("Create XTZ Transaction") {
    val walletName = "xtzWalletForCreateTX"
    assertWalletCreation(poolName, walletName, "tezos", Status.Ok)
    assertCreateAccount(CORRECT_BODY_XTZ, poolName, walletName, Status.Ok)
    val addresses = parse[Seq[FreshAddressView]](assertGetFreshAddresses(poolName, walletName, index = 0, Status.Ok))
    assert(addresses.nonEmpty)
    info(s"Here are addresses : $addresses")
    assertSyncAccount(poolName, walletName, 0)
    val operations = parse[Map[String, JsonNode]](assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(None, None, 1000, 0), Status.Ok))
    assert(operations.nonEmpty)

    // No fees, no feesLevel provided
    val add = addresses.head.address
    val receiverAddress = "tz2LLBZYevBRjNBvzJ24GbAkJ5bNFDQi3KQv"

/*    val txBadRequest = CreateXTZTransactionRequest(TezosOperationTag.OPERATION_TAG_TRANSACTION, receiverAddress,
                                                   "1000", false, Some("800"), Some("800"), None, None)
    val txBadRequestJson = server.mapper.writeValueAsString(txBadRequest)
    // Neither fees nor fees_level has been provided, expect failure due to MethodValidation check
    assertCreateTransaction(txBadRequestJson, poolName, walletName, 0, Status.BadRequest)*/

    // Not enough funds error
    val txTooPoorRequest = CreateXTZTransactionRequest(TezosOperationTag.OPERATION_TAG_TRANSACTION, receiverAddress,
                                                       "80000000", false, Some("800"), Some("800"), None, Some("FAST"))
    val txTooPoorRequestJson = server.mapper.writeValueAsString(txTooPoorRequest)
    assertCreateTransaction(txTooPoorRequestJson, poolName, walletName, 0, Status.BadRequest)

    // Check No fees amount provided with fee_level provided
    val txRequest = CreateXTZTransactionRequest(TezosOperationTag.OPERATION_TAG_TRANSACTION, receiverAddress,
                                                "1", false, Some("8"), Some("8"), None, Some("SLOW"))
    val txRequestJson = server.mapper.writeValueAsString(txRequest)
    val transactionView = parse[UnsignedTezosTransactionView](assertCreateTransaction(txRequestJson, poolName, walletName, 0, Status.Ok))
    info(s"Here is transaction view : $transactionView")
    assert(transactionView.operationType == TezosOperationTag.OPERATION_TAG_TRANSACTION)
    assert(transactionView.value == "1")
    assert(transactionView.signing_pubkey == "0288FE6B557457B08D15519288E33FAE30D646992B6C10FF18E69E3134D2A10113")
    assert(transactionView.receiver == Some(receiverAddress))
    assert(transactionView.sender == add)

    // Check normal speed fees multiplier
    val txNormalSpeedRequest = CreateXTZTransactionRequest(TezosOperationTag.OPERATION_TAG_TRANSACTION, receiverAddress,
                                                           "1", false, Some("8"), Some("8"), Some("322"), Some("NORMAL"))
    val txNormalSpeedRequestJson = server.mapper.writeValueAsString(txNormalSpeedRequest)
    val normalSpeedTransactionView = parse[UnsignedTezosTransactionView](assertCreateTransaction(txNormalSpeedRequestJson, poolName, walletName, 0, Status.Ok))
    info(s"Here is transaction view : $normalSpeedTransactionView")
    assert(normalSpeedTransactionView.fees == Some("644"))

    // Check fast speed multiplier
    val txFastSpeedRequest = CreateXTZTransactionRequest(TezosOperationTag.OPERATION_TAG_TRANSACTION, receiverAddress,
                                                         "1", false, Some("8"), Some("8"), Some("923"), Some("NORMAL"))
    val txFastSpeedRequestJson = server.mapper.writeValueAsString(txFastSpeedRequest)
    val fastSpeedTransactionView = parse[UnsignedTezosTransactionView](assertCreateTransaction(txFastSpeedRequestJson, poolName, walletName, 0, Status.Ok))
    info(s"Here is transaction view : $fastSpeedTransactionView")
    assert(fastSpeedTransactionView.fees == Some("1846"))
  }
}
