package co.ledger.wallet.daemon.database

import java.util.UUID

import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import co.ledger.wallet.daemon.models.Account.{Account, Derivation}
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult

import scala.concurrent.Future

trait DaemonCache {

  // ************** account *************
  def createAccount(accountDerivation: AccountDerivationView, user: User, poolName: String, walletName: String): Future[Account]

  def getAccounts(pubKey: String, poolName: String, walletName: String): Future[Seq[Account]]

  def getAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[Option[Account]]

  def getAccountOperations(user: User, accountIndex: Int, poolName: String, walletName: String, batch: Int, fullOp: Int): Future[PackedOperationsView]

  def getNextBatchAccountOperations(user: User, accountIndex: Int, poolName: String, walletName: String, next: UUID, fullOp: Int): Future[PackedOperationsView]

  def getPreviousBatchAccountOperations(user: User, accountIndex: Int, poolName: String, walletName: String, previous: UUID, fullOp: Int): Future[PackedOperationsView]

  def getNextAccountCreationInfo(pubKey: String, poolName: String, walletName: String, accountIndex: Option[Int]): Future[Derivation]

  // ************** currency ************
  def getCurrency(currencyName: String, poolName: String, pubKey: String): Future[Option[Currency]]

  def getCurrencies(poolName: String, pubKey: String): Future[Seq[Currency]]

  // ************** wallet *************
  def createWallet(walletName: String, currencyName: String, poolName: String, user: User): Future[Wallet]

  def getWallets(walletBulk: Bulk, poolName: String, pubKey: String): Future[(Int, Seq[Wallet])]

  def getWallet(walletName: String, poolName: String, pubKey: String): Future[Option[Wallet]]

  // ************** wallet pool *************
  def createWalletPool(user: User, poolName: String, configuration: String): Future[Pool]

  def getWalletPool(pubKey: String, poolName: String): Future[Option[Pool]]

  def getWalletPools(pubKey: String): Future[Seq[Pool]]

  def deleteWalletPool(user: User, poolName: String): Future[Unit]

  def syncOperations(): Future[Seq[SynchronizationResult]]

  //**************** user ***************
  def getUser(pubKey: String): Future[Option[User]]

  def createUser(user: UserDto): Future[Long]

}
