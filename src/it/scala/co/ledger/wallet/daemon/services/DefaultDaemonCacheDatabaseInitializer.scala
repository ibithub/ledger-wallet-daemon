package co.ledger.wallet.daemon.services

import java.util.UUID

import co.ledger.core.Wallet
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.database.DefaultDaemonCache
import co.ledger.wallet.daemon.models.Wallet.RichCoreWallet
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.utils.NativeLibLoader
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

trait DefaultDaemonCacheDatabaseInitializer extends BeforeAndAfterAll {
  self: Suite =>

  val defaultDaemonCache = new DefaultDaemonCache()

  override def beforeAll: Unit = {
    NativeLibLoader.loadLibs()
    Await.result(defaultDaemonCache.dbMigration, 1 second)
  }

  def createUser(pubKey: String = UUID.randomUUID().toString): DefaultDaemonCache.User = Await.result(defaultDaemonCache.createUser(pubKey, 1), 1 second)

  def initializedWallet(user: DefaultDaemonCache.User): Wallet = {
    Await.result(defaultDaemonCache.createWalletPool(PoolInfo(POOL_NAME, user.pubKey), ""), Duration.Inf)
    val wallet = Await.result(defaultDaemonCache.createWallet("bitcoin", WalletInfo(WALLET_NAME, POOL_NAME, user.pubKey), isNativeSegwit = false), Duration.Inf)
    val walletInfo = WalletInfo(WALLET_NAME, POOL_NAME, user.pubKey)
    Await.result(defaultDaemonCache.withWallet(walletInfo) {w =>
      w.addAccountIfNotExist(
        AccountDerivationView(0, List(
          DerivationView("44'/0'/0'", "main", Option("0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901"), Option("d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71")),
          DerivationView("44'/0'", "main", Option("0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901"), Option("d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71")))))}
      , Duration.Inf)
    Await.result(defaultDaemonCache.getAccountOperations(1, 1, AccountInfo(0, WALLET_NAME, POOL_NAME, user.pubKey)), Duration.Inf)
    Await.result(defaultDaemonCache.syncOperations(), Duration.Inf)

    wallet
  }
  private val WALLET_NAME = "WALLET_NAME"
  private val POOL_NAME = "POOL_NAME"


}
