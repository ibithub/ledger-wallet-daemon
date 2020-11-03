package co.ledger.wallet.daemon.utils

import akka.actor.ActorPath
import org.scalacheck.Prop
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}
import scalaz.concurrent.Task.Try


class AkkaUtilsTest extends FlatSpec with Checkers with Matchers {

  private val assertIsValid: String => Boolean = s => Try(ActorPath.validatePathElement(s)).toOption.nonEmpty

  "AkkaUtils.validActorName" should "build a valid actor element path" in {

    check(
      Prop.forAll { (poolName: String, walletName: String, actorIndex: Int) =>
        val actorName = AkkaUtils.validActorName(poolName, walletName, Math.abs(actorIndex).toString)
        assertIsValid(actorName)
      }, minSuccessful(30), sizeRange(3))
  }

  it should "keep string from element" in {
    val actorName = AkkaUtils.validActorName("poolName", "walletName", 3.toString)
    actorName should be ("poolName.walletName.3")
    assert( assertIsValid(actorName), s"$actorName is not valid")
  }

  it should "replace invalid char with _ " in {
    val actorName = AkkaUtils.validActorName("pool Name(3)!", "wallet Name", 3.toString)
    actorName should be ("pool_Name_3_!.wallet_Name.3")
    assert( assertIsValid(actorName), s"$actorName is not valid")
  }

}
