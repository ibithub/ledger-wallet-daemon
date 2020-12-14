package co.ledger.wallet.daemon.database

import java.util.UUID

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.models
import co.ledger.wallet.daemon.models.Wallet._
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.utils.NativeLibLoader
import org.junit.Assert._
import org.junit.{BeforeClass, Test}
import org.scalatest.junit.AssertionsForJUnit

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Try}

class DaemonCacheTest extends AssertionsForJUnit {

  import DaemonCacheTest._


  @Test def verifyGetPoolNotFound(): Unit = {
    val pool = Try(Await.result(cache.getWalletPool(PoolInfo("pool_not_exist", PUB_KEY_1)), Duration.Inf))
    assert(pool.isFailure)
    assert(pool.asInstanceOf[Failure[Option[Pool]]].exception.isInstanceOf[WalletPoolNotFoundException])
  }

  @Test def verifyDeleteNotExistPool(): Unit = {
    val user1 = Await.result(cache.getUser(PUB_KEY_1), Duration.Inf)
    Await.result(cache.deleteWalletPool(PoolInfo("pool_not_exist", user1.get.pubKey)), Duration.Inf)
  }

  @Test def verifyGetPoolsWithNotExistUser(): Unit = {
    try {
      Await.result(cache.getWalletPools("random"), Duration.Inf)
      fail()
    } catch {
      case _: UserNotFoundException => // expected
    }
  }

  @Test def verifyCreateAndGetPools(): Unit = {
    val pool11 = Await.result(cache.getWalletPool(PoolInfo("pool_1", PUB_KEY_1)), Duration.Inf)
    val pool12 = Await.result(cache.getWalletPool(PoolInfo("pool_2", PUB_KEY_1)), Duration.Inf)
    val pool13 = Await.result(cache.createWalletPool(PoolInfo("pool_3", new User(1L, PUB_KEY_1).pubKey), "config"), Duration.Inf)
    val pool1s = Await.result(cache.getWalletPools(PUB_KEY_1), Duration.Inf)
    assertEquals(3, pool1s.size)
    assertTrue(pool1s.contains(pool11.get))
    assertTrue(pool1s.contains(pool12.get))
    assertTrue(pool1s.contains(pool13))
  }

  @Test def verifyCreateAndDeletePool(): Unit = {
    val poolRandom = Await.result(cache.createWalletPool(PoolInfo("random", new User(2L, PUB_KEY_2).pubKey), "config"), Duration.Inf)
    val beforeDeletion = Await.result(cache.getWalletPools(PUB_KEY_2), Duration.Inf)
    assertEquals(3, beforeDeletion.size)
    assertTrue(beforeDeletion.contains(poolRandom))

    val afterDeletion = Await.result(cache.deleteWalletPool(PoolInfo(poolRandom.name, new User(2L, PUB_KEY_2).pubKey)).flatMap(_ => cache.getWalletPools(PUB_KEY_2)), Duration.Inf)
    assertFalse(afterDeletion.contains(poolRandom))
  }

  @Test def verifyGetCurrencies(): Unit = {
    val currencies = Await.result(cache.getCurrencies(PoolInfo("pool_1", PUB_KEY_1)), Duration.Inf)
    assert(currencies.size > 0)
    val currency = Await.result(cache.getCurrency("bitcoin", PoolInfo("pool_2", PUB_KEY_1)), Duration.Inf)
    assert(currency.isDefined)
  }

  @Test def verifyGetFreshAddressesFromNonExistingAccount(): Unit = {
    val user3 = Await.result(cache.getUser(PUB_KEY_3), Duration.Inf)
    val addresses: Seq[FreshAddressView] = Await.result(cache.getFreshAddresses(models.AccountInfo(0, WALLET_NAME, POOL_NAME, user3.get.pubKey)), Duration.Inf)
    assert(!addresses.isEmpty)
    try {
      Await.result(cache.getFreshAddresses(models.AccountInfo(1, WALLET_NAME, POOL_NAME, user3.get.pubKey)), Duration.Inf)
      fail()
    } catch {
      case _: AccountNotFoundException => // expected
    }
  }

  // FIXME: Broken test
  // @Test
  def verifyGetAccountOperations(): Unit = {
    val user1 = Await.result(cache.getUser(PUB_KEY_3), Duration.Inf)
    val pool1 = Await.result(DefaultDaemonCache.dbDao.getPool(user1.get.id, POOL_NAME), Duration.Inf)
    val withTxs = Await.result(cache.getAccountOperations(1, 1, AccountInfo(0, WALLET_NAME, pool1.get.name, user1.get.pubKey)), Duration.Inf)
    withTxs.operations.foreach(op => assertNotNull(op.transaction))
    val ops = Await.result(cache.getAccountOperations(2, 0, AccountInfo(0, WALLET_NAME, pool1.get.name, user1.get.pubKey)), Duration.Inf)
    assert(ops.previous.isEmpty)
    assert(2 === ops.operations.size)
    assert(ops.previous.isEmpty)
    ops.operations.foreach(op => assert(op.transaction.isEmpty))
    val nextOps = Await.result(cache.getNextBatchAccountOperations(ops.next.get, 0, AccountInfo(0, WALLET_NAME, pool1.get.name, user1.get.pubKey)), Duration.Inf)

    val previousOfNextOps = Await.result(cache.getPreviousBatchAccountOperations(nextOps.previous.get, 0, AccountInfo(0, WALLET_NAME, pool1.get.name, user1.get.pubKey)), Duration.Inf)
    assert(ops === previousOfNextOps)

    val nextnextOps = Await.result(cache.getNextBatchAccountOperations(nextOps.next.get, 0, AccountInfo(0, WALLET_NAME, pool1.get.name, user1.get.pubKey)), Duration.Inf)

    val previousOfNextNextOps = Await.result(cache.getPreviousBatchAccountOperations(nextnextOps.previous.get, 0, AccountInfo(0, WALLET_NAME, pool1.get.name, user1.get.pubKey)), Duration.Inf)
    assert(nextOps === previousOfNextNextOps)

    val theVeryFirstOps = Await.result(cache.getPreviousBatchAccountOperations(previousOfNextNextOps.previous.get, 0, AccountInfo(0, WALLET_NAME, pool1.get.name, user1.get.pubKey)), Duration.Inf)
    assert(ops === theVeryFirstOps)

    val maxi = Await.result(cache.getAccountOperations(Int.MaxValue, 0, AccountInfo(0, WALLET_NAME, pool1.get.name, user1.get.pubKey)), Duration.Inf)
    assert(maxi.operations.size < Int.MaxValue)
    assert(maxi.previous.isEmpty)
    assert(maxi.next.isEmpty)
  }
}

object DaemonCacheTest {
  @BeforeClass def initialization(): Unit = {
    NativeLibLoader.loadLibs()
    Await.result(cache.dbMigration, Duration.Inf)
    Await.result(DefaultDaemonCache.dbDao.insertUser(UserDto(PUB_KEY_1, 0, None)), Duration.Inf)
    Await.result(DefaultDaemonCache.dbDao.insertUser(UserDto(PUB_KEY_2, 0, None)), Duration.Inf)
    Await.result(DefaultDaemonCache.dbDao.insertUser(UserDto(PUB_KEY_3, 0, None)), Duration.Inf)
    val user1 = Await.result(cache.getUser(PUB_KEY_1), Duration.Inf)
    val user2 = Await.result(cache.getUser(PUB_KEY_2), Duration.Inf)
    val user3 = Await.result(cache.getUser(PUB_KEY_3), Duration.Inf)
    Await.result(cache.createWalletPool(PoolInfo("pool_1", user1.get.pubKey), ""), Duration.Inf)
    Await.result(cache.createWalletPool(PoolInfo("pool_2", user1.get.pubKey), ""), Duration.Inf)
    Await.result(cache.createWalletPool(PoolInfo("pool_1", user2.get.pubKey), ""), Duration.Inf)
    Await.result(cache.createWalletPool(PoolInfo("pool_3", user2.get.pubKey), ""), Duration.Inf)
    Await.result(cache.createWalletPool(PoolInfo(POOL_NAME, user3.get.pubKey), ""), Duration.Inf)
    Await.result(cache.createWallet("bitcoin", WalletInfo(WALLET_NAME, POOL_NAME, user3.get.pubKey), isNativeSegwit = false), Duration.Inf)
    val walletInfo = WalletInfo(WALLET_NAME, POOL_NAME, user3.get.pubKey)
    Await.result(cache.withWallet(walletInfo) { w =>
      w.addAccountIfNotExist(
        AccountDerivationView(0, List(
          DerivationView("44'/0'/0'", "main", Option("0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901"), Option("d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71")),
          DerivationView("44'/0'", "main", Option("0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901"), Option("d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71")))))
    }
      , Duration.Inf)
    Await.result(cache.getAccountOperations(1, 1, AccountInfo(0, WALLET_NAME, POOL_NAME, user3.get.pubKey)), Duration.Inf)
    Await.result(cache.syncOperations(), Duration.Inf)
    Await.result(cache.dbMigration, Duration.Inf)
  }

  private val cache: DefaultDaemonCache = new DefaultDaemonCache()
  private val PUB_KEY_1 = UUID.randomUUID().toString
  private val PUB_KEY_2 = UUID.randomUUID().toString
  private val PUB_KEY_3 = UUID.randomUUID().toString
  private val WALLET_NAME = "WALLET_NAME"
  private val POOL_NAME = "pool_name"
}