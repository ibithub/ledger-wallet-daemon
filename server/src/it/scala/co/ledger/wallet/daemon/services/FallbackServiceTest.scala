package co.ledger.wallet.daemon.services

import java.net.URL

import cats.data.OptionT
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.clients.{ClientFactory, ScalaHttpClientPool}
import com.twitter.inject.Logging
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@Test
class FallbackServiceTest extends AssertionsForJUnit with Logging {

  @Test
  def testEncodeBalance(): Unit = {
    val prefix = "0x"
    val address = "0xC4cFCE18f7Eb59c6FA50F86Ca20596B661415597"
    val addressRw = address.substring(2)
    val methodId = "70a08231"
    val padding = "000000000000000000000000"

    val res = FallbackService.encodeBalanceFunction(address)
    assert(res.isSuccess)
    assert(!res.get.isEmpty)
    assert(res.get.compareToIgnoreCase(prefix + methodId + padding + addressRw) == 0)
  }

  @Test
  def testGetERC20Balance(): Unit = {
    val contract = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
    val address = "0x2ad9e3f0c4cc4eff78cb24b06d6baab21037b185"
    val urlService: (URL, ScalaHttpClientPool) = ClientFactory.apiClient.fallbackService("ethereum").get

    val futurRes: OptionT[Future, BigInt] = FallbackService.getBalance(Some(contract), address, urlService._2, urlService._1)
    val res = Await.result(futurRes.value, Duration.Inf)
    // We expect defined Option, cannot bet on balance here (random account address)
    assert(res.isDefined)
    info(s"Fallback Service found for address $address, on contract $contract balance = ${res.get}")
  }

  @Test
  def testGetERC20BalanceUnknownAddress(): Unit = {
    val contract = "0x525794473F7ab5715C81d06d10f52d11cC052804"
    val address = "0xaf6754d07ee01d7c971486a5c6b9219d850ef6e1"
    val urlService: (URL, ScalaHttpClientPool) = ClientFactory.apiClient.fallbackService("ethereum").get

    val futurRes: OptionT[Future, BigInt] = FallbackService.getBalance(Some(contract), address, urlService._2, urlService._1)
    val res = Await.result(futurRes.value, Duration.Inf)
    // We expect defined Option, cannot bet on balance here (random account address)
    assert(res.isDefined)
    info(s"Fallback Service found for address $address, on contract $contract balance = ${res.get}")
  }

  @Test
  def testGetETHBalance(): Unit = {
    val address = "0x4133DBf072643e0c7928EcE20e83e24Bc08E30e3"
    val urlService: (URL, ScalaHttpClientPool) = ClientFactory.apiClient.fallbackService("ethereum").get

    val futurRes: OptionT[Future, BigInt] = FallbackService.getBalance(None, address, urlService._2, urlService._1)
    val res = Await.result(futurRes.value, Duration.Inf)
    // We expect defined Option, cannot bet on balance here (random account address)
    assert(res.isDefined)
    info(s"Fallback Service found for address $address balance = ${res.get}")
  }
}
