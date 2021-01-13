package co.ledger.wallet.daemon.services

import com.google.inject.{AbstractModule, Provides}
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.util.{ScheduledThreadPoolTimer, Timer}

object AccountSyncModule extends AbstractModule {
  @Provides def providesTimer: Timer = new ScheduledThreadPoolTimer(
    poolSize = 1,
    threadFactory = new NamedPoolThreadFactory("AccountSynchronizer-Scheduler")
  )

  override def configure(): Unit = ()
}
