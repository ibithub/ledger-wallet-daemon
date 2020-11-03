package co.ledger.wallet.daemon.modules

import akka.actor.{ActorRef, ActorSystem}
import co.ledger.core.{Account, Wallet}
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.services.AccountOperationsPublisher.PoolName
import co.ledger.wallet.daemon.services._
import com.google.inject.Provides
import com.twitter.inject.{Logging, TwitterModule}
import javax.inject.Singleton

import scala.util.{Failure, Success}

object PublisherModule extends TwitterModule with Logging {
  type OperationsPublisherFactory = (Account, Wallet, PoolName) => ActorRef

  @Singleton
  @Provides
  def providePublisher: Publisher = {
    info("Injecting Publisher ...")
    DaemonConfiguration.rabbitMQUri match {
      case Success(value) =>
        info("Injected RabbitMQPublisher")
        new RabbitMQPublisher(value)
      case Failure(_) =>
        info("No rabbitmq uri config found, injected DummyPublisher")
        new DummyPublisher
    }
  }

  @Singleton
  @Provides
  def providesActorSystem: ActorSystem = ActorSystem("wd-actor-system")

  @Provides
  @Singleton
  def provides(publishersManager: ActorSystem, publisher: Publisher): OperationsPublisherFactory = { (a, w, pn) =>
    publishersManager.actorOf(AccountOperationsPublisher.props(a, w, pn, publisher))
  }

}
