package co.ledger.wallet.daemon.api

import co.ledger.core.BitcoinLikePickingStrategy
import co.ledger.wallet.daemon.controllers.TransactionsController.CreateBTCTransactionRequest
import co.ledger.wallet.daemon.models.FreshAddressView
import co.ledger.wallet.daemon.models.coins.UnsignedBitcoinTransactionView
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.Status
import org.junit.Test

@Test
class TransactionsApiTest extends APIFeatureTest {

  val poolName = "ledger"

  override def beforeAll(): Unit = {
    super.beforeAll()
    createPool(poolName)
  }

  override def afterAll(): Unit = {
    deletePool(poolName)
    super.afterAll()
  }

  test("TransactionsApi# Exclude UTXO on create transaction") {
    val poolName = "transactionsExcludeUTXOTest"
    createPool(poolName)
    val walletName = "btcwalletutxo"
    assertWalletCreation(poolName, walletName, "bitcoin_testnet", Status.Ok)
    assertCreateAccount(ACCOUNT_BODY, poolName, walletName, Status.Ok)
    assertSyncAccount(poolName, walletName, 0)
    
    val btcTxRequest: CreateBTCTransactionRequest = CreateBTCTransactionRequest("mxZcpwZ7XBdfb4JcGLzdEP8WPQaGzeUeFU",
      Some("16"), Some("NORMAL"), "100", Some(BitcoinLikePickingStrategy.DEEP_OUTPUTS_FIRST.toString), None, Some(true))
    val btcTransacRequest = server.mapper.objectMapper.writeValueAsString(btcTxRequest)
    info(s"Parsed Transaction : ${btcTransacRequest}")
    val txView = parse[UnsignedBitcoinTransactionView](assertCreateTransaction(btcTransacRequest, poolName, walletName, 0, Status.Ok))
    assert(txView.inputs.nonEmpty)
    val utxo = txView.inputs.head
    assert(txView.inputs.count(u => u.previousTxHash == utxo.previousTxHash && u.previousOutputIndex == utxo.previousOutputIndex) == 1)

    val btcTxRequestExclude = btcTxRequest.copy(exclude_utxos = Some(Map{utxo.previousTxHash -> utxo.previousOutputIndex}))
    info(s"Exclude request : $btcTxRequestExclude")
    val btcTransacRequestExcludeUTXO = server.mapper.objectMapper.writeValueAsString(btcTxRequestExclude)
    val txViewExcluded = parse[UnsignedBitcoinTransactionView](assertCreateTransaction(btcTransacRequestExcludeUTXO, poolName, walletName, 0, Status.Ok))
    assert(txViewExcluded.inputs.nonEmpty)
    assert(txViewExcluded.inputs.count(u => u.previousTxHash == utxo.previousTxHash && u.previousOutputIndex == utxo.previousOutputIndex) == 0)
  }

  test("TransactionsApi#Create and sign BTC transaction") {
    val poolName = "transactionsCreation4Test"
    createPool(poolName)
    val walletName = "btcwallet"
    assertWalletCreation(poolName, walletName, "bitcoin_testnet", Status.Ok)
    assertCreateAccount(ACCOUNT_BODY, poolName, walletName, Status.Ok)
    val addresses = parse[Seq[FreshAddressView]](assertGetFreshAddresses(poolName, walletName, 0, Status.Ok))
    info(s"Addresses : $addresses")

    assertSyncAccount(poolName, walletName, 0)
    assertCreateTransaction(TX_BODY_WITH_EXCLUDE_UTXO, poolName, walletName, 0, Status.Ok)
    assertCreateTransaction(TX_BODY, poolName, walletName, 0, Status.BadRequest)
    assertCreateTransaction(INVALID_FEE_LEVEL_BODY, poolName, walletName, 0, Status.BadRequest)
    assertSignTransaction(TX_MISSING_APPENDED_SIG, poolName, walletName, 0, Status.BadRequest)
    assertSignTransaction(TX_MISSING_ONE_SIG, poolName, walletName, 0, Status.BadRequest)
    assertSignTransaction(TX_TO_SIGN_BODY, poolName, walletName, 0, Status.InternalServerError)
  }

  test("TransactionsApi# Create Partial Transaction") {
    val poolName = "transactionsPartialTest"
    createPool(poolName)
    val walletName = "btcwallet"
    assertWalletCreation(poolName, walletName, "bitcoin_testnet", Status.Ok)
    assertCreateAccount(ACCOUNT_BODY, poolName, walletName, Status.Ok)
    assertSyncAccount(poolName, walletName, 0)

    val btcTxRequestNonPartial: CreateBTCTransactionRequest = CreateBTCTransactionRequest("mxZcpwZ7XBdfb4JcGLzdEP8WPQaGzeUeFU",
      Some("16"), Some("NORMAL"), "100", Some(BitcoinLikePickingStrategy.DEEP_OUTPUTS_FIRST.toString), None, Some(false))
    val btcTransacNonPartialRequest = server.mapper.objectMapper.writeValueAsString(btcTxRequestNonPartial)
    info(s"Parsed Transaction : $btcTransacNonPartialRequest")
    val txView =
      parse[UnsignedBitcoinTransactionView](
        assertCreateTransaction(btcTransacNonPartialRequest, poolName, walletName, 0, Status.Ok))

    debug(s"TxView : $txView")
    assert(txView.inputs.forall(!_.previousTx.isEmpty))

    val btcTxRequest: CreateBTCTransactionRequest = CreateBTCTransactionRequest("mxZcpwZ7XBdfb4JcGLzdEP8WPQaGzeUeFU",
      Some("16"), Some("NORMAL"), "100", Some(BitcoinLikePickingStrategy.DEEP_OUTPUTS_FIRST.toString), None, Some(true))
    val btcTransacRequest = server.mapper.objectMapper.writeValueAsString(btcTxRequest)
    info(s"Parsed Transaction : ${btcTransacRequest}")
    val txViewPartial = parse[UnsignedBitcoinTransactionView](assertCreateTransaction(btcTransacRequest, poolName, walletName, 0, Status.Ok))

    debug(s"TxView : $txViewPartial")
    assert(txViewPartial.inputs.forall(_.previousTx.isEmpty))
  }

  test("AccountsApi#Broadcast signed BTC transaction") {
    val walletName = "bitcoin_testnet"
    assertWalletCreation(poolName, walletName, "bitcoin_testnet", Status.Ok)
    assertCreateAccount(ACCOUNT_BODY, poolName, walletName, Status.Ok)
    assertSyncAccount(poolName, walletName, 0)
    assertSignTransaction(TESTNET_TX_TO_SIGN_BODY, poolName, "bitcoin_testnet", 0, Status.InternalServerError)
    assertGetAccount(poolName, "bitcoin_testnet", 0, Status.Ok)
  }

  private val TX_TO_SIGN_BODY =
    """{
      |"raw_transaction": "0100000002A91F09D74BEE55D8E9F3673E42102FA9AB71185C47E83229076452C44EC301E9000000001976A91455F719785040EC522FB6CF9C4B45A7011912529188ACFFFFFFFFEA1B6F36DA6745A399878AB4B67BE9443A6155123BA1EFD252823F6987671095000000001976A914261E04A99A3E387DBA09A667F73C74E8C5A2523088ACFFFFFFFF02002D31010000000017A914394D7CE052572BF35DFC32CD6EFF5B4BE6D9300B870AC5E203000000001976A914F3CEB507BD0D264CE8B4C9564EA63E9426B3B66B88AC49EF0700",
      |"signatures": ["0100000002A91F0", "100000002A91F"],
      |"pubkeys": ["033B811F166EA0E8D764530960047A398F50AB89B40E70537DB06C303C7939930F","0229355FB9801567F6C332978F1383D7B6E717B7A3991524BC95F9D6A743DCA6CD"]
      |}""".stripMargin

  private val TX_MISSING_ONE_SIG =
    """{
      |"raw_transaction": "0100000002A91F09D74BEE55D8E9F3673E42102FA9AB71185C47E83229076452C44EC301E9000000001976A91455F719785040EC522FB6CF9C4B45A7011912529188ACFFFFFFFFEA1B6F36DA6745A399878AB4B67BE9443A6155123BA1EFD252823F6987671095000000001976A914261E04A99A3E387DBA09A667F73C74E8C5A2523088ACFFFFFFFF02002D31010000000017A914394D7CE052572BF35DFC32CD6EFF5B4BE6D9300B870AC5E203000000001976A914F3CEB507BD0D264CE8B4C9564EA63E9426B3B66B88AC49EF0700",
      |"signatures": ["0100000002A91F0"],
      |"pubkeys": ["033B811F166EA0E8D764530960047A398F50AB89B40E70537DB06C303C7939930F","0229355FB9801567F6C332978F1383D7B6E717B7A3991524BC95F9D6A743DCA6CD"]
      |}""".stripMargin

  private val TX_MISSING_APPENDED_SIG =
    """{
      |"raw_transaction": "0100000002A91F09D74BEE55D8E9F3673E42102FA9AB71185C47E83229076452C44EC301E9000000001976A91455F719785040EC522FB6CF9C4B45A7011912529188ACFFFFFFFFEA1B6F36DA6745A399878AB4B67BE9443A6155123BA1EFD252823F6987671095000000001976A914261E04A99A3E387DBA09A667F73C74E8C5A2523088ACFFFFFFFF02002D31010000000017A914394D7CE052572BF35DFC32CD6EFF5B4BE6D9300B870AC5E203000000001976A914F3CEB507BD0D264CE8B4C9564EA63E9426B3B66B88AC49EF0700",
      |"signatures": ["0100000002A91F0"],
      |"pubkeys": ["033B811F166EA0E8D764530960047A398F50AB89B40E70537DB06C303C7939930F"]
      |}""".stripMargin

  private val INVALID_FEE_LEVEL_BODY =
    """{""" +
      """"recipient": "mxZcpwZ7XBdfb4JcGLzdEP8WPQaGzeUeFU",""" +
      """"fees_level": "OTHER",""" +
      """"amount": 10000""" +
      """}"""

  private val TX_BODY =
    """{""" +
      """"recipient": "mxZcpwZ7XBdfb4JcGLzdEP8WPQaGzeUeFU",""" +
      """"fees_per_byte": 397000,""" +
      """"fees_level": "FAST",""" +
      """"amount": 10000""" +
      """}"""

  private val TX_BODY_WITH_EXCLUDE_UTXO =
    """{""" +
      """"recipient": "mxZcpwZ7XBdfb4JcGLzdEP8WPQaGzeUeFU",""" +
      """"fees_level": "NORMAL",""" +
      """"amount": 1000,""" +
      """"exclude_utxos":""" +
      """{""" +
      """"beabf89d72eccdcb895373096a402ae48930aa54d2b9e4d01a05e8f068e9ea49":0""" +
      """}""" +
      """}"""

  private val ACCOUNT_BODY =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/1'/0'",""" +
      """"pub_key": "0276b49de71f3032ba98f2988ae0a00b8c10011183007f2701aff60de1b272e45d",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/1'",""" +
      """"pub_key": "030d222dcc39de637d1a6ff646d600f4e26aad5af3b6a0ab9f979d1d3fb5c01b91",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]""" +
      """}"""

  private val TESTNET_TX_TO_SIGN_BODY =
    """{
      |"raw_transaction": "0100000001531ABD78576139559EA37E48A6554714D7434AE2BDF1451076A4C98219762AF20000000000FFFFFFFF02E8030000000000001976A9147F5365AABF5001DC5A3A21246E639B2C1FAD804888ACF4A03D00000000001976A914F9E27FEF11F7CE2F3EBE80535DD5AC812A85CCDD88AC8BC71300",
      |"signatures": ["3045022100DD6BA1732C7BD0E94F9FE71B6290E04A4A4B293B949FC1C585A0382EEADD62A0022072BFC3F077652C9B7EFC1C92969E00438AC0C7296CBA0A4B97AF08C9DAC36B74"],
      |"pubkeys": ["02A1DED78DAD86FE76E2238E29C58B549FBA769EF2601EBA643D96B17D3C6C4D4E"]
      |}""".stripMargin

}
