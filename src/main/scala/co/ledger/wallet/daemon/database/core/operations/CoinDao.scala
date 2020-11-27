package co.ledger.wallet.daemon.database.core.operations

import co.ledger.core.{Account, OperationType, Wallet}
import co.ledger.wallet.daemon.database.core.Database
import co.ledger.wallet.daemon.database.core.Database.SQLQuery
import co.ledger.wallet.daemon.models.Operations.OperationView
import com.twitter.finagle.postgres.Row
import com.twitter.util.Future


trait CoinDao {
  type OperationUid = String

  type TransactionUid = String

  protected val db: Database

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
  def findOperationsByUids(a: Account, w: Wallet, filteredUids: Seq[OperationUid], offset: Int, limit: Int): Future[Seq[OperationView]]

  /**
    * Count all operations grouped by types for given account
    */
  def countOperations(a: Account, w: Wallet): Future[Map[OperationType, Int]] =
    queryCountOperations[(OperationType, Int)](a.getIndex, w.getName) { row => {
      (OperationType.valueOf(row.get[String]("type")), row.get[Int]("count"))
    }
    }.map(seq => seq.groupBy(_._1).map { case (opType, count) => (opType, count.head._2) })


  private def queryCountOperations[T](accountIndex: Int, walletName: String)(f: Row => T) = {
    db.executeQuery[T](accountOperationQueryCount(accountIndex, walletName))(f)
  }

  private val accountOperationQueryCount: (Int, String) => SQLQuery = (accountIndex: Int, walletName: String) =>
    "SELECT Count(*) as count, o.type " +
      "FROM accounts a, wallets w, operations o " +
      s"WHERE w.name='$walletName' AND a.idx='$accountIndex' " +
      "AND a.wallet_uid=w.uid AND o.account_uid=a.uid " +
      "GROUP BY o.type"
}
