package co.ledger.wallet.daemon.database.core.operations

import java.util.Date

import co.ledger.core.{Account, OperationType, Wallet}
import co.ledger.wallet.daemon.database.core.Database.SQLQuery
import co.ledger.wallet.daemon.database.core.Decoder._
import co.ledger.wallet.daemon.database.core.{Database, Ordering}
import co.ledger.wallet.daemon.models.Operations.OperationView
import co.ledger.wallet.daemon.models.coins.{RippleMemoView, RippleTransactionView}
import com.twitter.finagle.postgres.Row
import com.twitter.inject.Logging
import com.twitter.util.Future

class RippleDao(protected val db: Database) extends CoinDao with Logging {
  logger.info(s"RippleDao created for ${db.client}")

  private val rippleOperationQuery: (Int, String, Ordering.OperationOrder, Option[Seq[OperationUid]], Int, Int) => SQLQuery =
    (accountIndex: Int, walletName: String, order: Ordering.OperationOrder, filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int) =>
      "SELECT o.uid, o.date, xop.transaction_hash, " +
        "b.height as block_height, b.time as block_time, b.hash as block_hash, o.type, o.amount, o.fees, o.senders, o.recipients, " +
        "rtx.sender, rtx.receiver, rtx.value, rtx.status, rtx.sequence, rtx.destination_tag " +
        "FROM accounts a, wallets w, operations o, ripple_operations xop, blocks b, ripple_transactions rtx " +
        s"WHERE w.name='$walletName' AND a.idx='$accountIndex' " +
        "AND a.wallet_uid=w.uid AND o.account_uid=a.uid AND o.uid = xop.uid AND o.block_uid=b.uid AND xop.transaction_uid = rtx.transaction_uid " +
        filteredUids.fold("")(uids => s"AND o.uid IN ('${uids.mkString("','")}') ") +
        "ORDER BY o.date " + order.value +
        s" OFFSET $offset LIMIT $limit"

  private val rippleTransactionMemosQuery: Seq[TransactionUid] => SQLQuery = (opUids: Seq[TransactionUid]) =>
    "SELECT rop.uid, mem.array_index, mem.data, mem.fmt, mem.ty " +
      "FROM ripple_memos mem " +
      "JOIN ripple_operations rop ON rop.transaction_uid = mem.transaction_uid " +
      s"WHERE rop.uid IN ('${opUids.mkString("','")}') " +
      "ORDER BY rop.uid ASC, mem.array_index ASC"

  def findRippleMemos(opUids: Seq[OperationUid]): Future[Seq[(OperationUid, RippleMemoView)]] = {
    queryRippleMemos[(OperationUid, RippleMemoView)](opUids) {
      row => {
        val opUid = row.get[String]("uid")
        val memoView = RippleMemoView(
          row.getOption[String]("data").getOrElse(""),
          row.getOption[String]("fmt").getOrElse(""),
          row.getOption[String]("ty").getOrElse("")
        )
        (opUid, memoView)
      }
    }
  }

  /**
    * List operations from an account
    */
  override def listAllOperations(a: Account, w: Wallet, offset: Int, limit: Int): Future[Seq[OperationView]] = {
    findOperationsByUids(a, w, Seq(), offset, limit)
  }

  /**
    * Find Operation
    */
  override def findOperationByUid(a: Account, w: Wallet, uid: OperationUid, offset: Int, limit: Int): Future[Option[OperationView]] = {
    findOperationsByUids(a, w, Seq(uid), offset, limit).map(_.headOption)

  }

  /**
    * List operations from an account filtered by Uids
    */
  override def findOperationsByUids(a: Account, w: Wallet, filteredUids: Seq[OperationUid], offset: Int, limit: Int): Future[Seq[OperationView]] = {
    logger.info(s"Retrieving operations for account : $a - limit=$limit offset=$offset")
    val currency = w.getCurrency
    val currencyName = currency.getName
    val currencyFamily = currency.getWalletType
    var uids = Seq[OperationUid]()

    def retrieveRippleOperation = {
      queryRippleOperations[RippleOperation](a.getIndex, w.getName, Some(filteredUids), offset, limit) {
        row => {
          val opUid = row.get[String]("uid")
          uids = uids :+ opUid
          RippleOperation(
            opUid, currencyName, currencyFamily.name(),
            row.get[Date]("date"),
            row.get[String]("transaction_hash"),
            row.getOption[Long]("block_height"),
            row.getOption[String]("block_hash"),
            row.getOption[Date]("block_time"),
            row.get[String]("type"),
            BigInt(row.get[String]("amount"), 16),
            BigInt(row.get[String]("fees"), 16),
            w.getName, a.getIndex,
            row.get[String]("senders").split(','),
            row.get[String]("recipients").split(','),
            row.get[String]("sender"),
            row.get[String]("receiver"),
            row.get[String]("value"),
            row.get[Int]("status"),
            BigInt(row.get[String]("sequence")),
            row.getOption[String]("destination_tag").map(BigInt(_)).getOrElse(BigInt(0))
          )
        }
      }
    }

    for {
      operations <- retrieveRippleOperation
      memos <- findRippleMemos(uids)
    } yield {
      val opMemos = memos.groupBy(_._1).map { case (opUid, memo) => (opUid, memo.map(_._2).toList) }
      operations.map(pop => {
        val confirmations = 0

        val ledgerSequence = pop.blockHeight.toString // it seems it ledgersequence is the block hieght
        val signingPubkey = "" // no use
        val txView = Some(RippleTransactionView(pop.txHash, pop.fees.toString(), pop.receiver, pop.sender, pop.value,
          pop.date, pop.status, pop.sequence.toString(), ledgerSequence, signingPubkey,
          opMemos.getOrElse(pop.uid, List.empty[RippleMemoView]),
          pop.destination_tag.longValue()))

        OperationView(
          pop.uid, currencyName, currencyFamily, None, confirmations,
          pop.date,
          pop.blockHeight,
          OperationType.valueOf(pop.opType),
          pop.amount.toString(),
          pop.fees.toString(),
          w.getName,
          a.getIndex,
          pop.senders,
          pop.recipients,
          pop.recipients.filter(a.getAccountKeychain.contains(_)),
          txView)

      })

    }
  }

  private def queryRippleOperations[T](accountIndex: Int, walletName: String, filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int)(f: Row => T) = {
    db.executeQuery[T](rippleOperationQuery(accountIndex, walletName, Ordering.Ascending, filteredUids, offset, limit))(f)
  }

  private def queryRippleMemos[T](opUids: Seq[OperationUid])(f: Row => T) = {
    db.executeQuery[T](rippleTransactionMemosQuery(opUids))(f)
  }

  case class RippleOperation(uid: String,
                             currencyName: String,
                             currencyFamily: String,
                             date: Date,
                             txHash: String,
                             blockHeight: Option[Long],
                             blockHash: Option[String],
                             blockTime: Option[Date],
                             opType: String,
                             amount: BigInt,
                             fees: BigInt,
                             walletName: String,
                             accountIndex: Int,
                             senders: Seq[String],
                             recipients: Seq[String],
                             sender: String,
                             receiver: String,
                             value: String,
                             status: Int,
                             sequence: BigInt,
                             destination_tag: BigInt)

}
