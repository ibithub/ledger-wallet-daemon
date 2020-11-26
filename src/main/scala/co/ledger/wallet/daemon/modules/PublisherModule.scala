package co.ledger.wallet.daemon.modules

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import co.ledger.core.{Account, Wallet}
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.services.AccountOperationsPublisher.PoolName
import co.ledger.wallet.daemon.services._
import com.google.inject.Provides
import com.twitter.inject.{Logging, TwitterModule}
import javax.inject.Singleton

import scala.util.{Failure, Success}

object PublisherModule extends TwitterModule with Logging {
  type OperationsPublisherFactory = (ActorRefFactory, DaemonCache, Account, Wallet, PoolName) => ActorRef

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
  def provides(publisher: Publisher): OperationsPublisherFactory = { (factory, cache, a, w, pn) =>
    factory.actorOf(AccountOperationsPublisher
      .props(cache, a, w, pn, publisher)
      .withDispatcher(SynchronizationDispatcher.configurationKey(SynchronizationDispatcher.Publisher)),
      name = AccountOperationsPublisher.name(a, w, pn))
  }
}
