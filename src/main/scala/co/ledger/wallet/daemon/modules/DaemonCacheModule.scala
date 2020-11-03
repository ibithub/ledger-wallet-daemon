package co.ledger.wallet.daemon.modules

import co.ledger.wallet.daemon.context.ApplicationContext.IOPool
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.database.{DaemonCache, DefaultDaemonCache}
import co.ledger.wallet.daemon.services.AccountSynchronizerManager
import com.google.inject.Provides
import com.twitter.inject.{Injector, TwitterModule}
import com.twitter.util.Duration
import javax.inject.Singleton

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object DaemonCacheModule extends TwitterModule {

  @Singleton
  @Provides
  def provideDaemonCache: DaemonCache = {
    val cache = new DefaultDaemonCache()
    val t0 = System.currentTimeMillis()
    Await.result(cache.dbMigration, 1.minutes)
    info(s"Database migration end, elapsed time: ${System.currentTimeMillis() - t0} milliseconds")
    cache
  }

  override def singletonPostWarmupComplete(injector: Injector): Unit = {
    info(s"Core operation pool cpu factor is ${DaemonConfiguration.corePoolOpSizeFactor}")
    Await.result(updateWalletConfig(), 5.minutes)
    val accountSynchronizerManager = injector.instance[AccountSynchronizerManager](classOf[AccountSynchronizerManager])
    accountSynchronizerManager.start()
  }

  override def singletonShutdown(injector: Injector): Unit = {
    info("Shutdown Hook...")
    injector.instance[AccountSynchronizerManager](classOf[AccountSynchronizerManager]).close(Duration.fromMinutes(3))
  }

  private def updateWalletConfig(): Future[Unit] = {
    for {
      allPools <- provideDaemonCache.getAllPools
    } yield allPools.map(pool => {
      for {
        wallets <- pool.wallets
      } yield wallets.map(pool.updateWalletConfig)
    })
  }
}
