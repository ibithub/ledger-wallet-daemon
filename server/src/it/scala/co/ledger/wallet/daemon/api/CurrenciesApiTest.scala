package co.ledger.wallet.daemon.api

import co.ledger.core
import co.ledger.wallet.daemon.controllers.responses.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.exceptions.ErrorCodes
import co.ledger.wallet.daemon.models.coins.BitcoinNetworkParamsView
import co.ledger.wallet.daemon.models.{CurrencyView, UnitView}
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.{Response, Status}

class CurrenciesApiTest extends APIFeatureTest {

  test("CurrenciesApi#Get currency with given pool name and currency name returns OK") {
    val response: Response = assertCurrency(CURRENCY_POOL, CURRENCY_BTC, Status.Ok)
    val currency: CurrencyView = parse[CurrencyView](response)
    assert(currency == EXPECTED_BTC_CURRENCY)
  }

  test("CurrenciesApi#Validate address for a given currency should return OK") {
    val valid: Response = assertValidateAddress(CURRENCY_POOL, CURRENCY_BTC, "349hWpJmJduwdZmSMnKYv4jULNjKLsnjLH", Status.Ok)
    assert(parse[Boolean](valid))
    val invalid: Response = assertValidateAddress(CURRENCY_POOL, CURRENCY_BTC, "bc1dafad", Status.Ok)
    assert(!parse[Boolean](invalid))
  }

  test("CurrenciesApi#Get currency from non-existing pool returns bad request") {
    val rep = parse[ErrorResponseBody](assertCurrency(CURRENCY_NON_EXIST_POOL, CURRENCY_BTC, Status.BadRequest))
    assert(rep.rc === ErrorCode.Bad_Request)
    assert(rep.msg.getOrElse("error_code", 0) === ErrorCodes.WALLET_POOL_NOT_FOUND)
  }

  test("CurrenciesApi#Get non-supported currency from existing pool returns currency not found") {
    assert(parse[ErrorResponseBody](assertCurrency(CURRENCY_POOL, CURRENCY_NON_EXIST, Status.NotFound)).rc
      == ErrorCode.Not_Found)
  }

  test("CurrenciesApi#Get currencies returns all") {
    val response: Response = assertCurrencies(CURRENCY_POOL, Status.Ok)
    val currencies: List[CurrencyView] = parse[List[CurrencyView]](response)
    assert(currencies.size > 0)
  }

  test("CurrenciesApi#Get currencies from non-existing pool returns bad request") {
    val rep = parse[ErrorResponseBody](assertCurrencies(CURRENCY_NON_EXIST_POOL, Status.BadRequest))
    assert(rep.rc == ErrorCode.Bad_Request)
    assert(rep.msg.getOrElse("error_code", 0) === ErrorCodes.WALLET_POOL_NOT_FOUND)
  }

  private def assertCurrency(poolName: String, currencyName: String, expected: Status): Response = {
    server.httpGet(s"/pools/$poolName/currencies/$currencyName", headers = defaultHeaders, andExpect = expected)
  }

  private def assertValidateAddress(poolName: String, currencyName: String, address: String, expected: Status): Response = {
    server.httpGet(s"/pools/$poolName/currencies/$currencyName/validate?address=$address", headers = defaultHeaders, andExpect = expected)
  }

  private def assertCurrencies(poolName: String, expected: Status): Response = {
    server.httpGet(s"/pools/$poolName/currencies", headers = defaultHeaders, andExpect = expected)
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    createPool(CURRENCY_POOL)
  }

  override def afterAll(): Unit = {
    deletePool(CURRENCY_POOL)
    super.afterAll()
  }

  private val CURRENCY_POOL = "currency_pool"
  private val CURRENCY_BTC = "bitcoin"
  private val CURRENCY_NON_EXIST = "currency_non_exist"
  private val CURRENCY_NON_EXIST_POOL = "non_exist_pool"
//  private val EXPECTED_BTC_TESTNET = CurrencyView(
//    "bitcoin_testnet",
//    core.WalletType.BITCOIN,
//    1,
//    "bitcoin",
//    List(
//      UnitView("testnet satoshi","satoshi","satoshi",0),
//      UnitView("testnet bitcoin", "BTC", "BTC", 8),
//      UnitView("testnet milli-bitcoin","mBTC", "mBTC", 5),
//      UnitView("testnet micro-bitcoin", "μBTC", "μBTC", 2),
//    ),
//    BitcoinNetworkParamsView("btc_testnet", "6F", "C4", "043587CF", "PER_BYTE", 546, "Bitcoin signed message:\n", false)
//  )
  private val EXPECTED_BTC_CURRENCY = CurrencyView(
    "bitcoin",
    core.WalletType.BITCOIN,
    0,
    "bitcoin",
    List(
      UnitView("satoshi","satoshi","satoshi",0),
      UnitView("bitcoin", "BTC", "BTC", 8),
      UnitView("milli-bitcoin","mBTC", "mBTC", 5),
      UnitView("micro-bitcoin", "μBTC", "μBTC", 2),
      ),
    BitcoinNetworkParamsView("btc", "00", "05", "0488B21E", "PER_BYTE", 546, "Bitcoin signed message:\n", false, true)
  )

}

