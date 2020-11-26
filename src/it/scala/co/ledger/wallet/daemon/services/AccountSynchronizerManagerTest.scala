package co.ledger.wallet.daemon.services

import akka.actor.ActorRef
import co.ledger.wallet.daemon.services.AccountSyncModule.AccountSynchronizerFactory
import com.twitter.util.{Duration, Time, Timer, TimerTask}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps


class AccountSynchronizerManagerTest extends FlatSpec with MockitoSugar with DefaultDaemonCacheDatabaseInitializer with Matchers with ScalaFutures {

  implicit val timeout: FiniteDuration = 1 minute

  "AccountSynchronizerManager.start" should "register accounts and schedule a periodic registration" in {

    var scheduled = false

    val scheduler = new Timer {
      override protected def scheduleOnce(when: Time)(f: => Unit): TimerTask = mock[TimerTask]

      override protected def schedulePeriodically(when: Time, period: Duration)(f: => Unit): TimerTask = {
        scheduled = true
        mock[TimerTask]
      }

      override def stop(): Unit = ()
    }

    val manager = new AccountSynchronizerManager(defaultDaemonCache, mock[AccountSynchronizerFactory], scheduler)

    Await.result(manager.start(), 1.minute)

    scheduled should be(true)
  }

  it should "register an AccountSynchronizer for each user" in {

    val _ = initializedWallet()

    var numberOfSynchronizerCreated = 0

    Await.result(new AccountSynchronizerManager(defaultDaemonCache, { (_, _, _, _) =>
      numberOfSynchronizerCreated += 1
      mock[ActorRef]
    }, mock[Timer]).start(), 1.minute)
    numberOfSynchronizerCreated should be >= 1
  }

}
