package co.ledger.wallet.daemon.services

import co.ledger.wallet.daemon.modules.PublisherModule.OperationsPublisherFactory
import com.twitter.util.{Duration, Time, Timer, TimerTask}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.language.postfixOps


class AccountSynchronizerManagerTest extends FlatSpec with MockitoSugar with DefaultDaemonCacheDatabaseInitializer with Matchers with ScalaFutures {

  implicit val timeout : FiniteDuration = 1 minute

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

    val manager = new AccountSynchronizerManager(defaultDaemonCache, { (_, _, _, _) => mock[AccountSynchronizer] }, mock[OperationsPublisherFactory], scheduler)

    manager.start().futureValue

    scheduled should be (true)
  }

  it should "register an AccountSynchronizer for each user" in {

    val user1 = createUser()
    val _ = initializedWallet(user1)

    var numberOfSynchronizerCreated = 0

    new AccountSynchronizerManager(defaultDaemonCache, { (_, _, _, _) =>
      numberOfSynchronizerCreated += 1
      mock[AccountSynchronizer] }, mock[OperationsPublisherFactory], mock[Timer]).start().futureValue

    numberOfSynchronizerCreated should be >= 1
  }

}
