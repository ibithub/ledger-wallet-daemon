package co.ledger.wallet.daemon.services

import akka.actor.{ActorRef, ActorSystem, Props}
import co.ledger.core.{Account, Wallet}
import co.ledger.wallet.daemon.modules.PublisherModule
import co.ledger.wallet.daemon.services.AccountOperationsPublisher.PoolName
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.util.{ScheduledThreadPoolTimer, Timer}

object AccountSyncModule extends AbstractModule {
  type AccountSynchronizerFactory = (Account, Wallet, PoolName) => ActorRef

  @Provides def providesTimer: Timer = new ScheduledThreadPoolTimer(
    poolSize = 1,
    threadFactory = new NamedPoolThreadFactory("AccountSynchronizer-Scheduler")
  )

  @Provides @Singleton
  def providesAccountSynchronizer(actorSystem: ActorSystem, publisherFactory : PublisherModule.OperationsPublisherFactory ) : AccountSynchronizerFactory = {
    (a, w, p ) => actorSystem.actorOf(
      Props(new AccountSynchronizer(a, w, p, publisherFactory))
      .withDispatcher(SynchronizationDispatcher.configurationKey(SynchronizationDispatcher.Synchronizer)),
      name = AccountSynchronizer.name(a, w, p)
    )
  }

  override def configure(): Unit = ()
}
