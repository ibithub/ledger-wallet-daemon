package co.ledger.wallet.daemon.modules

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import co.ledger.core.{Account, Wallet}
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.services.AccountOperationsPublisher.PoolName
import co.ledger.wallet.daemon.services._
import com.google.inject.Provides
import com.newmotion.akka.rabbitmq.{ChannelActor, ConnectionActor, CreateChannel}
import com.rabbitmq.client.{BuiltinExchangeType, Channel, ConnectionFactory}
import com.twitter.inject.{Logging, TwitterModule}
import com.typesafe.config.{Config, ConfigFactory}
import javax.inject.{Named, Singleton}

import scala.collection.mutable
import scala.util.{Failure, Success}

object PublisherModule extends TwitterModule  with Logging {
  type OperationsPublisherFactory = (ActorRefFactory, DaemonCache, Account, Wallet, PoolName) => ActorRef
  type PoolPublisher = PoolName => Publisher

  @Singleton
  @Provides
  def providesConfig() : Config = ConfigFactory.load()


  @Singleton
  @Provides
  def providePoolPublisher(@Named("RabbitMQConnection") connectionRabbitMQ: ActorRef, system: ActorSystem): PoolPublisher = {

    val createdPoolPublishers : mutable.Set[PoolName] = mutable.Set.empty

    { poolName =>

      val setupPublisher : (Channel, ActorRef) => Unit = (c, _) => c.exchangeDeclare(poolName.name, BuiltinExchangeType.TOPIC, true)

      this.synchronized {
        if ( !createdPoolPublishers.contains(poolName) ) {
          info(s"Creating rabbit channel for pool ${poolName.name}")
          connectionRabbitMQ ! CreateChannel(ChannelActor.props(setupPublisher), Some(poolName.name))
          createdPoolPublishers.add(poolName)
        }
      }

      DaemonConfiguration.rabbitMQUri match {
        case Success(_) =>
          info(s"Channel akka location: ${system.actorSelection(connectionRabbitMQ.path / poolName.name)}")
          new RabbitMQPublisher(system.actorSelection(connectionRabbitMQ.path / poolName.name))
        case Failure(_) =>
          info("No rabbitmq uri config found, injected DummyPublisher")
          new DummyPublisher
      }
    }
  }

  @Singleton
  @Provides
  def providesActorSystem: ActorSystem = ActorSystem("wd-actor-system")

  @Singleton
  @Provides
  @Named("RabbitMQConnection")
  def providesConnectionActor(system: ActorSystem, config: Config): ActorRef = {
    val connectionFactory = new ConnectionFactory()
    connectionFactory.setUri(config.getString("rabbitmq.uri"))
    system.actorOf(ConnectionActor.props(connectionFactory), "akka-rabbitmq")
  }

  @Provides
  @Singleton
  def providesOperationsPublisherFactory(poolPublisher: PoolPublisher): OperationsPublisherFactory = { (factory, cache, a, w, pn) =>
    factory.actorOf(AccountOperationsPublisher
      .props(cache, a, w, pn, poolPublisher(pn))
      .withDispatcher(SynchronizationDispatcher.configurationKey(SynchronizationDispatcher.Publisher)),
      name = AccountOperationsPublisher.name(a, w, pn))
  }
}
