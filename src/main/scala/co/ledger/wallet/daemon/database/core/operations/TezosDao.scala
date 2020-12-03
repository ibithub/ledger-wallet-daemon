package co.ledger.wallet.daemon.database.core.operations

import java.util.Date

import co.ledger.core
import co.ledger.core.{Account, Currency, OperationType, Wallet}
import co.ledger.wallet.daemon.database.core.Decoder.date
import co.ledger.wallet.daemon.database.core.{Database, Ordering}
import co.ledger.wallet.daemon.models.Operations
import co.ledger.wallet.daemon.models.Operations.OperationView
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView
import co.ledger.wallet.daemon.models.coins.TezosTransactionView
import com.twitter.finagle.postgres.Row
import com.twitter.inject.Logging
import com.twitter.util.Future

case class TezosDao(protected val db: Database) extends CoinDao with Logging {
  logger.info(s"TezosDao created for ${db.client}")

  private val tezosFilterByUidsQuery: Option[Seq[OperationUid]] => OperationUid = {
    case Some(uids) =>
      s"AND o.uid IN (${uids.map(uid => s"'$uid'").mkString(",")})"
    case None =>
      ""
  }

  private val tezosOperationQuery: (Int, String, Ordering.OperationOrder, Option[Seq[OperationUid]], Int, Int) => OperationUid =
    (accountIndex: Int, walletName: String, order: Ordering.OperationOrder, filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int) =>
      s"""
        SELECT o.uid, o.senders, o.recipients, o.amount, o.fees, o.type, o.date, b.height, b.hash as block_hash,
               o.type, tt.hash, tt.public_key, tt.type, tt.gas_limit, tt.storage_limit, tt.status
        FROM operations o, tezos_operations to, tezos_transactions tt, wallets w, accounts a, blocks b
        WHERE w.name='$walletName' AND a.idx='$accountIndex' AND a.wallet_uid=w.uid AND o.account_uid=a.uid
          AND o.block_uid=b.uid AND to.uid=o.uid AND tt.uid=so.transaction_uid
        ${tezosFilterByUidsQuery(filteredUids)}
        ORDER BY o.date ${order.value}
        OFFSET $offset LIMIT $limit
      """.replaceAll("\\s", " ").replace("\n", " ")

  /**
    * List operations from an account
    */
  override def listAllOperations(a: Account, w: Wallet, offset: Int, limit: Int): Future[Seq[Operations.OperationView]] = {
    queryTezosOperations(a.getIndex, w.getName, None, offset, limit)(rowToOperationView(a, w.getCurrency, w))
  }

  /**
    * Find Operation
    */
  override def findOperationByUid(a: Account, w: Wallet, uid: OperationUid, offset: Int, limit: Int): Future[Option[Operations.OperationView]] = {
    queryTezosOperations(a.getIndex, w.getName, Some(List(uid)), offset, limit)(rowToOperationView(a, w.getCurrency, w)).map(_.headOption)
  }

  /**
    * List operations from an account filtered by Uids
    */
  override def findOperationsByUids(a: Account, w: Wallet, filteredUids: Seq[OperationUid], offset: Int, limit: Int): Future[Seq[Operations.OperationView]] = {
    queryTezosOperations(a.getIndex, w.getName, Some(filteredUids), offset, limit)(rowToOperationView(a, w.getCurrency, w))
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
      uid = row.get[String]("uid"),
      currencyName = c.getName,
      currencyFamily = c.getWalletType,
      trust = None,
      confirmations = blockHeight.getOrElse(0),
      time = row.get[Date]("date"),
      blockHeight = blockHeight,
      opType = OperationType.valueOf(row.get[String]("type")),
      amount = amount.toString(),
      fees = fees.toString(),
      walletName = w.getName,
      accountIndex = a.getIndex,
      senders = Seq(sender),
      recipients = Seq(recipient),
      selfRecipients = Seq(recipient),
      transaction = Some(rowToTransactionView(sender, recipient, amount, fees)(row))
    )
  }

  /**
    * Convert SQL row to transaction view
    */
  private def rowToTransactionView(sender: String, recipient: String, amount: BigInt, fees: BigInt)(row: Row): TransactionView = {
    new TezosTransactionView(
      operationType = core.TezosOperationTag.valueOf(row.get[String]("type")),
      hash = row.get[String]("hash"),
      fees = Some(fees).map(_.toString),
      receiver = Some(recipient),
      sender = sender,
      value = amount.toString,
      date = row.get[Date]("date"),
      signing_pubkey = row.get[String]("public_key"),
      counter = None, // we don't care
      gasLimit = Some(row.get[String]("gas_limit")).map(BigInt(_)),
      storageLimit = Some(row.get[String]("storage_limit")).map(BigInt(_)),
      blockHash = row.get[String]("block_hash"),
      status = row.get[String]("status").toInt
    )
  }

  private def queryTezosOperations[T](
                                       accountIndex: Int,
                                       walletName: String,
                                       filteredUids: Option[Seq[OperationUid]],
                                       offset: Int,
                                       limit: Int
                                     ): (Row => T) => Future[Seq[T]] = {
    db.executeQuery[T](tezosOperationQuery(accountIndex, walletName, Ordering.Ascending, filteredUids, offset, limit))
  }

}
