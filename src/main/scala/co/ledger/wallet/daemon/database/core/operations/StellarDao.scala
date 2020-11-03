package co.ledger.wallet.daemon.database.core.operations

import java.util.Date

import co.ledger.core.{Account, Currency, OperationType, StellarLikeMemoType, Wallet}
import co.ledger.wallet.daemon.database.core.{Database, Ordering}
import co.ledger.wallet.daemon.models.Operations
import co.ledger.wallet.daemon.database.core.Decoder._
import co.ledger.wallet.daemon.models.Operations.OperationView
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView
import co.ledger.wallet.daemon.models.coins.{StellarMemo, StellarTransactionView}
import com.twitter.finagle.postgres.Row
import com.twitter.inject.Logging
import com.twitter.util.Future

class StellarDao(protected val db: Database) extends CoinDao with Logging {
  logger.info(s"StellarDao created for ${db.client}")


  private val stellarFilterByUidsQuery: Option[Seq[OperationUid]] => OperationUid = {
      case Some(uids) =>
        s"AND o.uid IN (${uids.map(uid => s"'$uid'").mkString(",")})"
      case None =>
        ""
    }

  private val stellarOperationQuery: (Int, String, Ordering.OperationOrder, Option[Seq[OperationUid]], Int, Int) => OperationUid =
    (accountIndex: Int, walletName: String, order: Ordering.OperationOrder, filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int) =>
      s"""
        SELECT o.uid, o.senders, o.recipients, o.amount, o.fees, o.type, o.date, b.height,
               o.type, st.hash, st.successful, st.memo_type, st.memo
        FROM operations o, stellar_account_operations sao, stellar_operations so, stellar_transactions st, wallets w,
             accounts a, blocks b
        WHERE w.name='$walletName' AND a.idx='$accountIndex' AND a.wallet_uid=w.uid AND o.account_uid=a.uid
          AND o.block_uid=b.uid AND sao.uid=o.uid AND so.uid=sao.operation_uid AND st.uid=so.transaction_uid
        ${stellarFilterByUidsQuery(filteredUids)}
        ORDER BY o.date ${order.value}
        OFFSET $offset LIMIT $limit
      """.replaceAll("\\s", " ").replace("\n", " ")

  /**
    * List operations from an account
    */
  override def listAllOperations(a: Account, w: Wallet, offset: Int, limit: Int): Future[Seq[Operations.OperationView]] = {
    queryStellarOperations(a.getIndex, w.getName, None, offset, limit)(rowToOperationView(a, w.getCurrency, w))
  }

  /**
    * Find Operation
    */
  override def findOperationByUid(a: Account, w: Wallet, uid: OperationUid, offset: Int, limit: Int): Future[Option[Operations.OperationView]] = {
    queryStellarOperations(a.getIndex, w.getName, Some(Seq(uid)), offset, limit)(rowToOperationView(a, w.getCurrency, w)).map(_.headOption)
  }

  /**
    * List operations from an account filtered by Uids
    */
  override def findOperationsByUids(a: Account, w: Wallet, filteredUids: Seq[OperationUid], offset: Int, limit: Int): Future[Seq[Operations.OperationView]] = {
    queryStellarOperations(a.getIndex, w.getName, Some(filteredUids), offset, limit)(rowToOperationView(a, w.getCurrency, w))
  }

  /**
    * Convert a SQL Row to a operation view
    */
  private def rowToOperationView(a: Account, c: Currency, w: Wallet)(row: Row): OperationView = {
    val amount = BigInt(row.get[String]("amount"), 16)
    val fees = BigInt(row.get[String]("fees"), 16)
    val sender = row.get[String]("senders")
    val recipient = row.get[String]("recipients")
    val blockHeight = row.getOption[Long]("height")
    OperationView(
      row.get[String]("uid"),
      c.getName,
      c.getWalletType,
      None,
      blockHeight.getOrElse(0),
      row.get[Date]("date"),
      blockHeight,
      OperationType.valueOf(row.get[String]("type")),
      amount.toString(),
      fees.toString(),
      w.getName,
      a.getIndex,
      Seq(sender),
      Seq(recipient),
      Seq(recipient).filter(a.getAccountKeychain.contains(_)),
      Some(rowToTransactionView(sender, recipient, amount, fees)(row))
    )
  }

  /**
    * Convert SQL row to transaction view
    */
  private def rowToTransactionView(sender: String, recipient: String, amount: BigInt, fees: BigInt)(row: Row): TransactionView = {
    StellarTransactionView(
      row.get[String]("hash"),
      sender,
      recipient,
      amount.toString,
      fees.toString,
      rowToStellarMemo(row),
      row.get[Int]("successful") == 1
    )
  }

  /**
    * Convert SQL row to stellar memo
    */
  private def rowToStellarMemo(row: Row): Option[StellarMemo] = {
    StellarMemo.from(
      normalizeMemoType(row.get[String]("memo_type")).toString,
      row.get[String]("memo")
    )
  }

  private def normalizeMemoType(memoType: String): StellarLikeMemoType = memoType match {
    case "text" => StellarLikeMemoType.MEMO_TEXT
    case "id" => StellarLikeMemoType.MEMO_ID
    case "return" => StellarLikeMemoType.MEMO_RETURN
    case "hash" => StellarLikeMemoType.MEMO_HASH
    case _ => StellarLikeMemoType.MEMO_NONE
  }

  private def queryStellarOperations[T](accountIndex: Int, walletName: String,
                       filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int): (Row => T) => Future[Seq[T]] = {
    db.executeQuery[T](stellarOperationQuery(accountIndex, walletName, Ordering.Ascending, filteredUids, offset, limit))
  }

}
