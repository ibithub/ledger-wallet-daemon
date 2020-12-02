package co.ledger.wallet.daemon.api

import java.io.IOException
import java.util.UUID

import co.ledger.core.TimePeriod
import co.ledger.wallet.daemon.controllers.AccountsController.{HistoryResponse, TokenHistoryResponse, UtxoAccountResponse}
import co.ledger.wallet.daemon.models.Operations.PackedOperationsView
import co.ledger.wallet.daemon.models.{AccountDerivationView, AccountView, ERC20AccountView, FreshAddressView}
import co.ledger.wallet.daemon.services._
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer, JsonNode}
import com.twitter.finagle.http.{Response, Status}

class AccountsApiTest extends APIFeatureTest {
  val poolName = "default_test_pool"


  override def beforeAll(): Unit = {
    super.beforeAll()
    createPool(poolName)
  }

  override def afterAll(): Unit = {
    deletePool(poolName)
    super.afterAll()
  }

  test("AccountsApi#Get history") {
    assertWalletCreation(poolName, poolName, "bitcoin", Status.Ok)
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, poolName, Status.Ok)
    history(poolName, poolName, 0, "2017-10-12T13:38:23Z", "2018-10-12T13:38:23Z", TimePeriod.DAY.toString, Status.Ok)
    // deletePool(poolName)
  }

  test("AccountsApi#Get history bad requests") {
    // createPool(poolName)
    assertWalletCreation(poolName, poolName, "bitcoin", Status.Ok)
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, poolName, Status.Ok)
    history(poolName, poolName, 0, "2017-10-12", "2018-10-12T13:38:23Z", TimePeriod.DAY.toString, Status.BadRequest)
    history(poolName, poolName, 0, "2017-10-12T13:38:23Z", "2018-10-12", TimePeriod.DAY.toString, Status.BadRequest)
    history(poolName, poolName, 0, "2017-10-12T13:38:23Z", "2018-10-12T13:38:23Z", "TIME", Status.BadRequest)
    history(poolName, poolName, 0, "2018-11-12T13:38:23Z", "2018-10-12T13:38:23Z", TimePeriod.DAY.toString, Status.BadRequest)
    // test if period too long
    history(poolName, poolName, 0, "2015-11-12T00:00:00Z", "2015-13-12T00:00:00Z", TimePeriod.HOUR.toString, Status.BadRequest)
    history(poolName, poolName, 0, "2015-11-12T00:00:00Z", "2015-13-12T00:00:00Z", TimePeriod.DAY.toString, Status.Ok)
    history(poolName, poolName, 0, "2015-11-12T00:00:00Z", "2019-10-12T00:00:00Z", TimePeriod.DAY.toString, Status.BadRequest)
    history(poolName, poolName, 0, "2015-11-12T00:00:00Z", "2019-10-12T00:00:00Z", TimePeriod.WEEK.toString, Status.Ok)
    history(poolName, poolName, 0, "2015-11-12T00:00:00Z", "2029-10-12T00:00:00Z", TimePeriod.WEEK.toString, Status.BadRequest)
    history(poolName, poolName, 0, "2015-11-12T00:00:00Z", "2029-10-12T00:00:00Z", TimePeriod.MONTH.toString, Status.Ok)
    history(poolName, poolName, 0, "2015-11-12T00:00:00Z", "2219-10-12T00:00:00Z", TimePeriod.MONTH.toString, Status.BadRequest)
    // deletePool(poolName)
  }

  test("AccountsApi#Get empty accounts") {
    val walletName = "emptywallet"
    assertWalletCreation(poolName, walletName, "bitcoin", Status.Ok)
    val result = assertGetAccounts(None, poolName, walletName, Status.Ok)
    assert(parse[Seq[AccountView]](result).isEmpty)
  }

  test("AccountsApi#Get account with index from empty wallet") {
    assertWalletCreation(poolName, "individual_account_wallet", "bitcoin", Status.Ok)
    assertGetAccounts(Option(1), poolName, "individual_account_wallet", Status.NotFound)
  }

  test("AccountsApi#Get accounts same as get individual account") {
    // createPool(poolName)
    val walletName = "getaccountall"
    assertWalletCreation(poolName, walletName, "bitcoin", Status.Ok)
    val expectedAccount = parse[AccountView](assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, walletName, Status.Ok))
    val actualAccount = parse[AccountView](assertGetAccounts(Option(0), poolName, walletName, Status.Ok))
    assert(expectedAccount === actualAccount)
    val actualAccountList = parse[Seq[AccountView]](assertGetAccounts(None, poolName, walletName, Status.Ok))
    assert(List(actualAccount) === actualAccountList)
    // deletePool(poolName)
  }

  test("AccountsApi#Get fresh addresses from account") {
    // createPool(poolName)
    assertWalletCreation(poolName, poolName, "bitcoin", Status.Ok)
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, poolName, Status.Ok)
    val addresses = parse[Seq[FreshAddressView]](assertGetFreshAddresses(poolName, poolName, index = 0, Status.Ok))
    assert(addresses.nonEmpty)
    // deletePool(poolName)
  }

  test("AccountsApi#Get addresses by range") {
    // createPool(poolName)
    assertWalletCreation(poolName, "accounts_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, "accounts_wallet", Status.Ok)
    val r = parse[Seq[FreshAddressView]](getAddresses(poolName, "accounts_wallet", 0, 0, 1))
    assert(r.size == 4)
    // deletePool(poolName)
  }

  test("AccountsApi#Get utxo from btc account") {
    val walletName = "getutxo"
    assertWalletCreation(poolName, walletName, "bitcoin", Status.Ok)
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, walletName, Status.Ok)
    assertSyncAccount(poolName, walletName, 0)
    assertSyncPools(Status.Ok)
    val utxoList = parse[UtxoAccountResponse](assertGetUTXO(poolName, walletName, index = 0, Status.Ok))
    assert(utxoList.utxos.exists(_.address == "1N1zxviEVvKoCHd91wcJGuFLBQjhkWiBQ1"))
    assert(utxoList.utxos.exists(_.address == "19QXVtvE98WkhBAEPUApaMScbDsvQMc5DD"))
    assert(utxoList.utxos.exists(_.address == "13CaMweDMLgxLwNJ5asXLsmruQhnfzVkEg"))
    assert(5 === utxoList.count)
  }

  test("AccountsApi#Get account(s) from non exist pool return bad request") {
    assertCreateAccount(CORRECT_BODY_BITCOIN, "not_exist_pool", poolName, Status.BadRequest)
    assertGetAccounts(None, "not_exist_pool", poolName, Status.BadRequest)
    assertGetAccounts(Option(0), "not_exist_pool", poolName, Status.BadRequest)
  }

  test("AccountsApi#Get account(s) from non exist wallet return bad request") {
    // createPool(poolName)
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, "not_exist_wallet", Status.BadRequest)
    assertGetAccounts(None, poolName, "not_exist_wallet", Status.BadRequest)
    assertGetAccounts(Option(0), poolName, "not_exist_wallet", Status.BadRequest)
    // deletePool(poolName)
  }

  test("AccountsApi#Get next account creation info with index return Ok") {
    // createPool(poolName)
    assertWalletCreation(poolName, poolName, "bitcoin", Status.Ok)
    val actualResult = parse[AccountDerivationView](assertGetAccountCreationInfo(poolName, poolName, Option(0), Status.Ok))
    assert(0 === actualResult.accountIndex)
    // deletePool(poolName)
  }

  test("AccountsApi#Get next account creation info without index return Ok") {
    // createPool(poolName)
    assertWalletCreation(poolName, poolName, "bitcoin", Status.Ok)
    assertGetAccountCreationInfo(poolName, poolName, None, Status.Ok)
    // deletePool(poolName)
  }

  test("AccountsApi#Create account with no pubkey") {
    // createPool(poolName)
    assertWalletCreation(poolName, "accounts_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(MISSING_PUBKEY_BODY, poolName, "accounts_wallet", Status.BadRequest)
    // deletePool(poolName)
  }

  test("AccountsApi#Create account with invalid request body") {
    // createPool(poolName)
    assertWalletCreation(poolName, "accounts_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(MISSING_PATH_BODY, poolName, "accounts_wallet", Status.BadRequest)
    // deletePool(poolName)
  }

  test("AccountsApi#Create account fail core lib validation") {
    // createPool(poolName)
    assertWalletCreation(poolName, "accounts_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(INVALID_ARGUMENT_BODY, poolName, "accounts_wallet", Status.BadRequest)
    // deletePool(poolName)
  }

  test("AccountsApi#Create account on btc testnet") {
    // createPool(poolName)
    assertWalletCreation(poolName, "accounts_wallet", "bitcoin_testnet", Status.Ok)
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, "accounts_wallet", Status.Ok)
    // deletePool(poolName)
  }

  test("AccountsApi#Create account with request body as invalid json") {
    // createPool(poolName)
    assertWalletCreation(poolName, "accounts_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(INVALID_JSON, poolName, "accounts_wallet", Status.BadRequest)
    // deletePool(poolName)
  }

  test("AccountsApi#Create and Delete pool with wallet accounts") {
    val poolName = "create_delete_pool"
    val wallet1 = "account_deletePool_wal1"
    val wallet2 = "account_deletePool_wal2"
    val wallet3 = "account_deletePool_wal3"

    createPool(poolName)
    assertWalletCreation(poolName, wallet1, "bitcoin", Status.Ok) // no account associated
    assertWalletCreation(poolName, wallet2, "bitcoin", Status.Ok) // 1 account associated
    assertWalletCreation(poolName, wallet3, "bitcoin", Status.Ok) // 2 accounts
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, wallet2, Status.Ok)
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, wallet3, Status.Ok)
    assertCreateAccount(CORRECT_BODY_IDX1, poolName, wallet3, Status.Ok)

    val pool = getPool(poolName, Status.Ok)
    val walPoolView: JsonNode = server.mapper.objectMapper.readTree(pool.getContentString())
    assert(walPoolView.findValue("name").asText().equals(poolName))
    assert(walPoolView.findValue("wallet_count").asInt() == 3)
    info(s"Pool is = $pool and $walPoolView")
    deletePool(poolName)
    getPool(poolName, Status.NotFound)
  }


  // To be removed as wipe data is not proposed anymore, resync feature is proposed instead
  ignore("AccountsApi#Create account and wipe specific account") {
    // val poolName = "delete_account"
    val wallet1 = "test_deleteAcc_wal1"
    val wallet2 = "test_deleteAcc_wal2"
    val wallet3 = "test_deleteAcc_wal3"

    // Create Pool wallets and accounts
    // createPool(poolName)

    assertWalletCreation(poolName, wallet1, "bitcoin", Status.Ok) // no account associated
    assertWalletCreation(poolName, wallet2, "bitcoin", Status.Ok) // 1 account associated
    assertWalletCreation(poolName, wallet3, "bitcoin", Status.Ok) // 2 accounts
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, wallet2, Status.Ok)
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, wallet3, Status.Ok)
    assertCreateAccount(CORRECT_BODY_IDX1, poolName, wallet3, Status.Ok)

    // Sync
    assertSyncAccount(poolName, wallet2, 0)
    assertSyncAccount(poolName, wallet3, 0)
    assertSyncAccount(poolName, wallet3, 1)

    val countWall3A0 = transactionCountOf(poolName, wallet3, 0)

    // Get then Delete and check operation count for the 3 accounts
    assert(transactionCountOf(poolName, wallet2, 0) > 0)
    clearAccount(poolName, wallet2, 0, Status.Ok)
    assert(transactionCountOf(poolName, wallet2, 0) == 0)
    assert(transactionCountOf(poolName, wallet3, 0) > 0)
    assert(transactionCountOf(poolName, wallet3, 1) > 0)

    clearAccount(poolName, wallet3, 1, Status.Ok)
    assert(transactionCountOf(poolName, wallet2, 0) == 0)
    assert(transactionCountOf(poolName, wallet3, 0) > 0)
    assert(transactionCountOf(poolName, wallet3, 1) == 0)

    clearAccount(poolName, wallet3, 0, Status.Ok)
    assert(transactionCountOf(poolName, wallet2, 0) == 0)
    assert(transactionCountOf(poolName, wallet3, 0) == 0)
    assert(transactionCountOf(poolName, wallet3, 1) == 0)
    assertGetAccounts(Some(0), poolName, wallet2, Status.Ok)
    assertGetAccounts(Some(0), poolName, wallet3, Status.Ok)
    assertGetAccounts(Some(1), poolName, wallet3, Status.Ok)

    // Sync again and retrieve operations for a given account only (others should stay empty)
    assertSyncAccount(poolName, wallet3, 0)
    assert(transactionCountOf(poolName, wallet2, 0) == 0)
    val countWall3A0After = transactionCountOf(poolName, wallet3, 0)
    assert(countWall3A0After > 0)
    assert(countWall3A0After == countWall3A0)
    assert(transactionCountOf(poolName, wallet3, 1) == 0) // Still unchanged
    assertGetAccounts(Some(0), poolName, wallet2, Status.Ok)
    assertGetAccounts(Some(0), poolName, wallet3, Status.Ok)
    assertGetAccounts(Some(1), poolName, wallet3, Status.Ok)
  }

  private def transactionCountOf(poolName: String, walletName: String, accIdx: Int): Int = {
    val response = assertGetAccounts(Some(accIdx), poolName, walletName, Status.Ok)
    val jsonAccount: JsonNode = server.mapper.objectMapper.readTree(response.getContentString())
    val opsJson = jsonAccount.findValue("operation_count")

    val received = Option(opsJson.findValue("RECEIVE")) match {
      case Some(v) => v.intValue()
      case _ => 0
    }

    val sent = Option(opsJson.findValue("SEND")) match {
      case Some(v) => v.intValue()
      case _ => 0
    }
    sent + received
  }

  test("AccountsApi#Get account operations") {
    def getUUID(field: String, content: Map[String, JsonNode]): Option[UUID] = {
      val idStr = content.get(field).map(_.asText())
      idStr.map(UUID.fromString)
    }

    val walletName = "op_wallet"
    assertWalletCreation(poolName, walletName, "bitcoin", Status.Ok)
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, walletName, Status.Ok)
    assertSyncPools(Status.Ok)
    assertGetAccountOp(poolName, walletName, 0, "noexistop", 0, Status.NotFound)
    assertGetAccountOp(poolName, walletName, 0, "1e05a587ed9c697f192623514fddd619a454e8c4b0b2bec7e3026f0e8abc7e05", 0, Status.Ok)
    val response = assertGetFirstOperation(0, poolName, walletName, Status.Ok).contentString
    assert(response.contains("10780ee61c3fd032d5a65034bdd2a1bb1295fc724c5b93b86bc6ee55c075d213"))

    val firstBtch = parse[Map[String, JsonNode]](assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(None, None, 2, 0), Status.Ok))
    val secondBtch = parse[Map[String, JsonNode]](assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(None, getUUID("next", firstBtch), 10, 0), Status.Ok))

    val previousOf2ndBtch = parse[Map[String, JsonNode]](assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(getUUID("previous", secondBtch), None, 10, 0), Status.Ok))
    assert(firstBtch.get("next") === previousOf2ndBtch.get("next"))
    assert(firstBtch.get("previous") === previousOf2ndBtch.get("previous"))

    val thirdBtch = parse[Map[String, JsonNode]](assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(None, getUUID("next", secondBtch), 5, 0), Status.Ok))
    val fourthBtch = parse[Map[String, JsonNode]](assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(None, getUUID("next", thirdBtch), 10, 0), Status.Ok))
    val previousOf4thBtch = parse[Map[String, JsonNode]](assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(getUUID("previous", fourthBtch), None, 10, 1), Status.Ok))
    assert(thirdBtch.get("next") === previousOf4thBtch.get("next"))
    assert(thirdBtch.get("previous") === previousOf4thBtch.get("previous"))
    // deletePool(poolName)
  }

  test("AccountsApi#Pool not exist") {
    // createPool(poolName)
    val walletName = "poolUnknwonwWallet"
    assertWalletCreation(poolName, walletName, "bitcoin", Status.Ok)
    assertCreateAccount(CORRECT_BODY_BITCOIN, poolName, walletName, Status.Ok)
    assertGetAccountOps("op_pool_non_exist", walletName, 0, OperationQueryParams(None, None, 2, 0), Status.BadRequest)
    assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(None, Option(UUID.randomUUID), 2, 0), Status.BadRequest)
    assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(Option(UUID.randomUUID), None, 2, 0), Status.BadRequest)
    // deletePool(poolName)
  }

  private val CORRECT_BODY_ETH =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/60'/0'",""" +
      """"extended_key": "xpub6EM1gLShjupLBK87mLsXQer5Q3VrqCXehd7e37jrQQx7aGUwMrym2mLjcmZWVuh2bscKooHvXov5D16VzFbc8ou77pnHhQ4y5m2mT5FKi2r"""" +
      """}""" +
      """]""" +
      """}"""

  private val CORRECT_BODY_ETH_ROPSTEN =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/60'/0'",""" +
      """"pub_key": "045650BE990F3CD39DF6CBAEBB8C06646727B1629509F993883681AE815EE1F3F76CC4628A600F15806D8A25AE164C061BF5EAB3A01BD8A7E8DB3BAAC07629DC67",""" +
      """"chain_code": "81F18B05DF5F54E5602A968D39AED1ED4EDC146F5971C4E84AA8273376B05D49"""" + """}""" +
      """]""" +
      """}"""

  test("AccountsApi#Create ETH account") {
    val walletName = "ethWallet"
    // createPool(poolName)
    assertWalletCreation(poolName, walletName, "ethereum", Status.Ok)
    assertCreateAccountExtended(CORRECT_BODY_ETH, poolName, walletName, Status.Ok)
    val addresses = parse[Seq[FreshAddressView]](assertGetFreshAddresses(poolName, walletName, index = 0, Status.Ok))
    info(s"ETH address : $addresses")
    assert(addresses.size == 1)
    assertSyncAccount(poolName, walletName, 0)
    assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(None, None, 5, 0), Status.Ok)
    // deletePool(poolName)
  }

  test("AccountsApi#Create ETH ropsten account") {
    val walletName = "ethRopstenWallet"
    // createPool(poolName)
    assertWalletCreation(poolName, walletName, "ethereum_ropsten", Status.Ok)
    assertCreateAccount(CORRECT_BODY_ETH_ROPSTEN, poolName, walletName, Status.Ok)
    // Expect one address on eth accounts
    val addresses = parse[Seq[FreshAddressView]](assertGetFreshAddresses(poolName, walletName, index = 0, Status.Ok))
    info(s"ETH address : $addresses")
    assert(addresses.size == 1)

    // Sync and get operations, check the two firsts pages does not have any intersections
    assertSyncAccount(poolName, walletName, 0)

    val response: PackedOperationsView = parse[PackedOperationsView](assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(None, None, 5, 0), Status.Ok))
    val response2: PackedOperationsView = parse[PackedOperationsView](assertGetAccountOps(poolName, walletName, 0, OperationQueryParams(None, response.next, 5, 0), Status.Ok))
    assert(response.operations.size == 5)
    assert(response2.operations.size == 5)
    assert(response2.operations.intersect(response.operations).isEmpty)

    // Balance history on 3 days, end exclusive, no new operations on the interval
    val historyResponse = parse[HistoryResponse](history(poolName, walletName, 0, "2020-04-13T00:00:00Z", "2020-04-16T00:00:00Z", TimePeriod.DAY.toString, Status.Ok))
    assert(historyResponse.balances.size == 3)
    assert(historyResponse.balances.head == historyResponse.balances.tail.head)
    assert(historyResponse.balances.tail.head == historyResponse.balances.tail.tail.head)

    // Check that balance = 1.976594768488753681 Ether (https://ropsten.etherscan.io/address/0x2EaDEDe7034243Bd2E8a574E80aFDD60409AE5c4)
    assert(historyResponse.balances.head == BigInt.apply(1976594768488753681L))
    // deletePool(poolName)
  }


  protected def assertGetTokens(poolName: String, walletName: String, idx: Int, expected: Status): Response = {
    server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts/$idx/tokens", andExpect = expected)
  }

  protected def assertGetTokensOperation(poolName: String, walletName: String, idx: Int, contractAddress: String, expected: Status): Response = {
    server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts/$idx/tokens/$contractAddress/operations", andExpect = expected)
  }

  protected def assertGetTokensOperation(poolName: String, walletName: String, idx: Int, contractAddress: String, batch: Int, offset: Int, expected: Status): Response = {
    server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts/$idx/tokens/$contractAddress/operations?batch=$batch&offset=$offset", andExpect = expected)
  }

  protected def assertGetTokensHistory(poolName: String, walletName: String, idx: Int, contractAddress: String, start: String, end: String, timeInterval: String, expected: Status): Response = {
    server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts/$idx/tokens/$contractAddress/history?start=$start&end=$end&time_interval=$timeInterval", andExpect = expected)
  }

  test("AccountsApi#Create ERC20 ropsten account") {
    val walletName = "ethRopstenTokenWallet"
    // createPool(poolName)
    assertWalletCreation(poolName, walletName, "ethereum_ropsten", Status.Ok)
    assertCreateAccount(CORRECT_BODY_ETH_ROPSTEN, poolName, walletName, Status.Ok)

    assertSyncAccount(poolName, walletName, 0)
    val resp = parse[List[ERC20AccountView]](assertGetTokens(poolName, walletName, 0, Status.Ok))
    info(s"Tokens : $resp")
    assert(resp.size >= 2)
    val contractAddress = "0x57e8ba2A915285f984988282aB9346c1336a4E11"

    val allOperations = parse[List[JsonNode]](assertGetTokensOperation(poolName, walletName, 0, contractAddress, Status.Ok))
    assert(allOperations.nonEmpty)

    val batchOperations = parse[List[JsonNode]](assertGetTokensOperation(poolName, walletName, 0, contractAddress, 10, 0, Status.Ok))
    assert(batchOperations.containsSlice(allOperations.slice(0, 10)))

    val histoResponse = parse[TokenHistoryResponse](assertGetTokensHistory(poolName, walletName, 0, contractAddress, "2020-04-13T00:00:00Z", "2020-04-16T00:00:00Z", TimePeriod.DAY.toString, Status.Ok))
    info(s"histo : $histoResponse")
    assert(histoResponse.balances.size == 3)
    assert(histoResponse.balances.head == BigInt("100000000000000000000"))
    // deletePool(poolName)
  }


  val module: SimpleModule = new SimpleModule
  module.addDeserializer(classOf[SyncStatus], new AccountStatusDeserializer)
  server.mapper.registerModule(module)

  class AccountStatusDeserializer extends JsonDeserializer[SyncStatus] {
    override def deserialize(jp: JsonParser, ctxt: DeserializationContext): SyncStatus = {
      val jTree: JsonNode = jp.getCodec.readTree[JsonNode](jp)
      jTree.get("value").asText match {
        case "synced" => Synced(atHeight = jTree.get("at_height").asLong)
        case "syncing" => Syncing(fromHeight = jTree.get("from_height").asLong, currentHeight = jTree.get("current_height").asLong)
        case "failed" => FailedToSync(reason = jTree.get("reason").asText)
        case _ => throw new IOException(s"Failed to deserialize $jTree")
      }
    }
  }

  private val CORRECT_BODY_BITCOIN =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'/0'",""" +
      """"pub_key": "0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]""" +
      """}"""

  private val CORRECT_BODY_IDX1 =
    """{""" +
      """"account_index": 1,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'/1'",""" +
      """"pub_key": "0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]""" +
      """}"""

  private val INVALID_ARGUMENT_BODY =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "different than next owner",""" +
      """"path": "44'/0'/0'",""" +
      """"pub_key": "0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]""" +
      """}"""

  private val MISSING_PUBKEY_BODY =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'/0'",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]""" +
      """}"""

  private val MISSING_PATH_BODY =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"pub_key": "0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]""" +
      """}"""

  private val INVALID_JSON =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'/0'",""" +
      """"pub_key": "0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]"""
}
