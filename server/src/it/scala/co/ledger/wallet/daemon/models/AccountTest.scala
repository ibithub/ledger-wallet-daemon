package co.ledger.wallet.daemon.models

import java.util.UUID

import co.ledger.core
import co.ledger.core.Address
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.database.PoolDto
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.Wallet._
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import co.ledger.wallet.daemon.utils.NativeLibLoader
// import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class AccountTest extends AssertionsForJUnit {
  NativeLibLoader.loadLibs()

  private val PUBKEYS = List[String](
    "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",
    "0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901")
  private val CHAINCODES = List[String](
    "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb",
    "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71")

  private val testPool = Pool.newInstance(Await.result(Pool.newCoreInstance(PoolDto(UUID.randomUUID().toString, 2L, "", Option(0L))), Duration.Inf), 1L)

  private val testWallet = Await.result(testPool.addWalletIfNotExist("test_wallet", "bitcoin", isNativeSegwit = false), Duration.Inf)

  private val account0: core.Account = Await.result(
    testWallet.accountCreationInfo(Option(0)).map { derivation =>
      AccountDerivationView(
        derivation.index,
        derivation.view.derivations.zipWithIndex.map { d =>
          DerivationView(d._1.path, d._1.owner, Option(PUBKEYS(d._2)), Option(CHAINCODES(d._2)))
        }
      )
    }.flatMap { info => testWallet.addAccountIfNotExist(info)
      .flatMap { a => a.sync(testPool.name, testWallet.getName).map { syncResult =>
        assert(SynchronizationResult(0, testWallet.getName, testPool.name, syncResult = true) === syncResult)
        a } } }, Duration.Inf)

  private val account1: core.Account = Await.result(
    testWallet.accountCreationInfo(Option(1)).map { derivation =>
      AccountDerivationView(
        derivation.index,
        derivation.view.derivations.zipWithIndex.map { d =>
          DerivationView(d._1.path, d._1.owner, Option(PUBKEYS(d._2)), Option(CHAINCODES(d._2)))
        }
      )
    }.flatMap { info => testWallet.addAccountIfNotExist(info) }, Duration.Inf)

  private val account2: core.Account = Await.result(
    testWallet.accountCreationInfo(Option(2)).map { derivation =>
      AccountDerivationView(
        derivation.index,
        derivation.view.derivations.zipWithIndex.map { d =>
          DerivationView(d._1.path, d._1.owner, Option(PUBKEYS(d._2)), Option(CHAINCODES(d._2)))
        }
      )
    }.flatMap { info => testWallet.addAccountIfNotExist(info) }, Duration.Inf)

  private val freshAddresses: Seq[Address] = Await.result(account2.freshAddresses, Duration.Inf)

  // FIXME: Broken test
  // @Test
  def verifyAccountCreation(): Unit = {
    assert(0 === account0.getIndex)
    assert(1 === account1.getIndex)
    assert(2 === account2.getIndex)
    val emptyOp = Await.result(account0.operation("nonexistoperation", 1), Duration.Inf)
    assert(emptyOp.isEmpty)
    val operations = Await.result(account0.operations(0, 1, 1), Duration.Inf)
    assert(1 === operations.size)
    val headOp = Await.result(account0.operation(operations.head.getUid, 1), Duration.Inf)
    assert(headOp.map(_.getUid) === Option(operations.head.getUid))
    assert(freshAddresses.nonEmpty)
  }
}
