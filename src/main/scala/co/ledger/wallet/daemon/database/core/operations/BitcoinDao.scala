package co.ledger.wallet.daemon.database.core.operations

import java.util.Date

import co.ledger.core.{Account, OperationType, Wallet}
import co.ledger.wallet.daemon.database.core.Decoder._
import co.ledger.wallet.daemon.database.core.{Database, Ordering, PartialOperation}
import co.ledger.wallet.daemon.models.Operations.OperationView
import co.ledger.wallet.daemon.models.coins.{BitcoinInputView, BitcoinOutputView, BitcoinTransactionView, CommonBlockView}
import com.twitter.finagle.postgres.Row
import com.twitter.inject.Logging
import com.twitter.util.Future

class BitcoinDao(db: Database) extends CoinDao with Logging {
  logger.info(s"BitcoinDao created for ${db.client}")

  private val btcInputQuery: (Seq[OperationUid], Int, Int) => OperationUid = (opUids: Seq[OperationUid], offset: Int, limit: Int) =>
    "SELECT bop.uid, bins.address, bins.amount, bins.coinbase, bins.previous_tx_hash, bins.previous_output_idx " +
      "FROM bitcoin_operations bop, bitcoin_transactions bt, bitcoin_inputs bins, bitcoin_transaction_inputs btxi " +
      "WHERE bop.transaction_uid = bt.transaction_uid " +
      "AND btxi.transaction_uid = bt.transaction_uid " +
      "AND btxi.input_uid = bins.uid " +
      s"AND bop.uid IN ('${opUids.mkString("','")}') " +
      s"OFFSET $offset LIMIT $limit"

  private val btcOutputQuery: (Seq[OperationUid], Int, Int) => OperationUid = (opUids: Seq[OperationUid], offset: Int, limit: Int) =>
    "SELECT bop.uid, bt.hash, bout.idx, bout.amount, bout.address, bout.script " +
      "FROM bitcoin_operations bop, bitcoin_transactions bt, bitcoin_outputs bout " +
      "WHERE bt.transaction_uid = bop.transaction_uid " +
      "AND bout.transaction_uid = bt.transaction_uid " +
      s"AND bop.uid IN ('${opUids.mkString("','")}') " +
      s"OFFSET $offset LIMIT $limit"

  private val btcOperationQuery: (Int, String, Ordering.OperationOrder, Option[Seq[OperationUid]], Int, Int) => OperationUid =
    (accountIndex: Int, walletName: String, order: Ordering.OperationOrder, filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int) =>
      "SELECT o.uid, o.date, bop.transaction_hash, b.height as block_height, b.time as block_time, b.hash as block_hash, o.type, o.amount, o.fees, o.senders, o.recipients " +
        "FROM accounts a, wallets w, operations o, bitcoin_operations bop, blocks b " +
        s"WHERE w.name='$walletName' AND a.idx='$accountIndex' " +
        "AND a.wallet_uid=w.uid AND o.account_uid=a.uid AND o.uid = bop.uid AND o.block_uid=b.uid " +
        filteredUids.fold("")(uids => s"AND o.uid IN ('${uids.mkString("','")}') ") +
        "ORDER BY o.date " + order.value +
        s" OFFSET $offset LIMIT $limit"

  /**
    * List operations from an account
    */
  def listAllOperations(a: Account, w: Wallet, offset: Int, limit: Int): Future[Seq[OperationView]] = {
    listOperationsByUids(a, w, None, offset, limit)
  }

  /**
    * Find Operation
    */
  def findOperationByUid(a: Account, w: Wallet, uid: OperationUid, offset: Int, limit: Int): Future[Option[OperationView]] = {
    listOperationsByUids(a, w, Some(Seq(uid)), offset, limit).map(_.headOption)
  }

  /**
    * List operations from an account filtered by Uids
    */
  def listOperationsByUids(a: Account, w: Wallet, filteredUids: Seq[OperationUid], offset: Int, limit: Int): Future[Seq[OperationView]] = {
    listOperationsByUids(a, w, Some(filteredUids), offset, limit)
  }

  /**
    * List operations from an account filtered by Uids
    */
  def listOperationsByUids(a: Account, w: Wallet, filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int): Future[Seq[OperationView]] = {
    logger.info(s"Retrieving operations for account : $a - limit=$limit offset=$offset")
    val currency = w.getCurrency
    val currencyName = currency.getName
    val currencyFamily = currency.getWalletType
    var uids = Seq[OperationUid]()

    def retrievePartialOperations = {
      queryBitcoinOperations[PartialOperation](a.getIndex, w.getName, filteredUids, offset, limit) {
        row => {
          val opUid = row.get[String]("uid")
          uids = uids :+ opUid
          PartialOperation(
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
            row.get[String]("recipients").split(',')
          )
        }
      }
    }

    for {
      operations <- retrievePartialOperations
      outputs <- findBitcoinOutputs(uids)
      inputs <- findBitcoinInputs(uids)
    } yield {
      val opIntputs = inputs.groupBy(_._1).map { case (opUid, pair) => (opUid, pair.map(_._2)) }
      val opOutputs = outputs.groupBy(_._1).map { case (opUid, pair) => (opUid, pair.map(_._2)) }

      operations.map(pop => {
        val confirmations = 0 // pop.blockHeight.fold(0L)(opHeight => (lastBlock.getHeight - opHeight) + 1)
        val txView = (opIntputs.get(pop.uid), opOutputs.get(pop.uid)) match {
          case (Some(inputs), Some(outputs)) =>
            val blockView = (pop.blockHash, pop.blockHeight, pop.blockTime) match {
              case (Some(hash), Some(height), Some(time)) => Some(CommonBlockView(hash, height, time))
              case _ => None
            }
            Some(BitcoinTransactionView(blockView, Some(pop.fees.toString()), pop.txHash, pop.date, inputs, 0L, outputs))
          case _ => None
        }
        OperationView(pop.uid, currencyName, currencyFamily, None, confirmations,
          pop.date,
          pop.blockHeight,
          OperationType.valueOf(pop.opType),
          pop.amount.toString(),
          pop.fees.toString(),
          w.getName,
          a.getIndex,
          pop.senders,
          pop.recipients,
          pop.recipients.filter(a.getAccountKeychain.contains(_)), txView)

      })
    }
  }

  // TODO : offset / limit for resilience
  def findBitcoinOutputs(opUids: Seq[OperationUid]): Future[Seq[(OperationUid, BitcoinOutputView)]] =
    queryBitcoinOutputs[(OperationUid, BitcoinOutputView)](opUids, 0, Int.MaxValue) {
      row =>
        (row.get[String]("uid"), BitcoinOutputView(
          row.get[String]("address"),
          row.get[String]("hash"),
          row.get[Int]("idx"),
          row.get[String]("amount"),
          row.get[String]("script"))
        )
    }

  def findBitcoinInputs(opUids: Seq[OperationUid]): Future[Seq[(OperationUid, BitcoinInputView)]] = {
    queryBitcoinInputs[(OperationUid, BitcoinInputView)](opUids, 0, Int.MaxValue) {
      row => {
        val opUid = row.get[String]("uid")
        val outputView = BitcoinInputView(
          row.get[String]("address"),
          row.get[String]("amount"),
          row.getOption[String]("coinbase"),
          row.getOption[String]("previous_tx_hash"),
          row.getOption[Int]("previous_output_idx")
        )
        (opUid, outputView)
      }
    }
  }

  private def queryBitcoinOutputs[T](opUids: Seq[OperationUid], offset: Int, limit: Int)(f: Row => T) = {
    db.executeQuery[T](btcOutputQuery(opUids, offset, limit))(f)
  }

  private def queryBitcoinInputs[T](opUids: Seq[OperationUid], offset: Int, limit: Int)(f: Row => T) = {
    db.executeQuery[T](btcInputQuery(opUids, offset, limit))(f)
  }

  private def queryBitcoinOperations[T](accountIndex: Int, walletName: String, filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int)(f: Row => T) = {
    db.executeQuery[T](btcOperationQuery(accountIndex, walletName, Ordering.Ascending, filteredUids, offset, limit))(f)
  }
}
