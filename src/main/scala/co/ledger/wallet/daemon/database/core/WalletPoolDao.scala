package co.ledger.wallet.daemon.database.core

import co.ledger.core.{Wallet, WalletType}
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.database.core.operations.{BitcoinDao, CoinDao, EthereumDao, RippleDao, StellarDao}
import co.ledger.wallet.daemon.models.AccountInfo
import co.ledger.wallet.daemon.models.Operations.OperationView
import com.twitter.inject.Logging
import com.twitter.util.Future

import scala.concurrent.ExecutionContext

class WalletPoolDao(poolName: String)(implicit val ec: ExecutionContext) extends CoinDao with Logging {
  val db: Database = new Database(DaemonConfiguration.coreDbConfig, poolName)

  lazy val btcDao = new BitcoinDao(db)
  lazy val ethDao = new EthereumDao(db)
  lazy val xrpDao = new RippleDao(db)
  lazy val xlmDao = new StellarDao(db)

  /**
    * List operations from an account
    */
  override def listAllOperations(a: AccountInfo, w: Wallet, offset: Int, limit: Int): Future[Seq[OperationView]] =
    daoForWalletType(w.getWalletType).listAllOperations(a, w, offset, limit)

  /**
    * Find Operation
    */
  override def findOperationByUid(a: AccountInfo, w: Wallet, uid: OperationUid, offset: Int, limit: Int): Future[Option[OperationView]] =
    daoForWalletType(w.getWalletType).findOperationByUid(a, w, uid, offset, limit)

  /**
    * List operations from an account filtered by Uids
    */
  override def listOperationsByUids(a: AccountInfo, w: Wallet, filteredUids: Seq[OperationUid], offset: Int, limit: Int): Future[Seq[OperationView]] =
    daoForWalletType(w.getWalletType).listOperationsByUids(a, w, filteredUids, offset, limit)

  def daoForWalletType(wt: WalletType): CoinDao = wt match {
    case WalletType.BITCOIN => btcDao
    case WalletType.ETHEREUM => ethDao
    case WalletType.RIPPLE => xrpDao
    case WalletType.STELLAR => xlmDao
    case _ => throw new NotImplementedError(s"Dao not implemented for $wt")
  }
}

object WalletPoolDao {
  def apply(name: String)(implicit ec: ExecutionContext): WalletPoolDao = new WalletPoolDao(name)
}
