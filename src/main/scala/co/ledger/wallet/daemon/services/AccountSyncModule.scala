package co.ledger.wallet.daemon.services

import co.ledger.core.{Account, Wallet}
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.util.{ScheduledThreadPoolTimer, Timer}

import scala.concurrent.ExecutionContext

object AccountSyncModule extends AbstractModule {
  type PoolName = String
  type AccountSynchronizerFactory = (Account, Wallet, PoolName, ExecutionContext) => AccountSynchronizer

  @Provides def providesTimer: Timer = new ScheduledThreadPoolTimer(
    poolSize = 1,
    threadFactory = new NamedPoolThreadFactory("AccountSynchronizer-Scheduler")
  )

  @Provides @Singleton
  def providesAccountSynchronizer(publisher: Publisher, scheduler : Timer) : AccountSynchronizerFactory = {
    (a, w, p, ec) => new AccountSynchronizer(a, w, p, scheduler, publisher)(ec)
  }

  override def configure(): Unit = ()
}
