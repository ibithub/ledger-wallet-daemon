package co.ledger.wallet.daemon.database.core.operations

import co.ledger.core.{Account, Wallet}
import co.ledger.wallet.daemon.models.Operations.OperationView
import com.twitter.util.Future


trait CoinDao {
  type OperationUid = String

  /**
    * List operations from an account
    */
  def listAllOperations(a: Account, w: Wallet, offset: Int, limit: Int): Future[Seq[OperationView]]

  /**
    * Find Operation
    */
  def findOperationByUid(a: Account, w: Wallet, uid: OperationUid, offset: Int, limit: Int): Future[Option[OperationView]]

  /**
    * List operations from an account filtered by Uids
    */
  def listOperationsByUids(a: Account, w: Wallet, filteredUids: Seq[OperationUid], offset: Int, limit: Int): Future[Seq[OperationView]]

}
