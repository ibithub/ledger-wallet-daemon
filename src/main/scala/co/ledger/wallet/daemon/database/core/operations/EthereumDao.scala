package co.ledger.wallet.daemon.database.core.operations

import java.util.Date

import co.ledger.core.{Account, OperationType, Wallet}
import co.ledger.wallet.daemon.database.core.Decoder._
import co.ledger.wallet.daemon.database.core.{Database, Ordering, PartialEthOperation}
import co.ledger.wallet.daemon.models.Operations.OperationView
import co.ledger.wallet.daemon.models.coins.{CommonBlockView, EthereumTransactionView}
import com.twitter.finagle.postgres.Row
import com.twitter.inject.Logging
import com.twitter.util.Future

class EthereumDao(db: Database) extends CoinDao with Logging {
  logger.info(s"EthereumDao created for ${db.client}")

  private val ethOperationQuery: (Int, String, Ordering.OperationOrder, Option[Seq[OperationUid]], Int, Int) => OperationUid =
    (accountIndex: Int, walletName: String, order: Ordering.OperationOrder, filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int) =>
      "SELECT o.uid, o.date, eop.transaction_hash, b.height as block_height, b.time as block_time, b.hash as block_hash, " +
        "o.type, o.amount, o.fees, o.senders, o.recipients, etx.gas_price, etx.gas_limit, etx.gas_used, etx.status, etx.confirmations " +
        "FROM accounts a, wallets w, operations o, ethereum_operations eop, ethereum_transactions etx, blocks b " +
        s"WHERE w.name='$walletName' AND a.idx='$accountIndex' " +
        "AND a.wallet_uid=w.uid AND o.account_uid=a.uid AND o.uid = eop.uid AND o.block_uid=b.uid AND etx.transaction_uid = eop.transaction_uid " +
        filteredUids.fold("")(uids => s"AND o.uid IN ('${uids.mkString("','")}') ") +
        "ORDER BY o.date " + order.value +
        s" OFFSET $offset LIMIT $limit"

  private val erc20OperationQuery: (Int, String, Ordering.OperationOrder, Option[Seq[OperationUid]], Int, Int) => OperationUid =
    (accountIndex: Int, walletName: String, order: Ordering.OperationOrder, filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int) =>
      "SELECT ercop.uid as erc_uid, ercop.ethereum_operation_uid as eth_uid, receiver, value " +
        "FROM wallets w, erc20_operations ercop, erc20_accounts ercacc, ethereum_accounts ethacc " +
        s"WHERE w.name='$walletName' AND ethacc.idx='$accountIndex' " +
        "AND ethacc.wallet_uid = w.uid AND ercop.account_uid = ercacc.uid AND ercacc.ethereum_account_uid = ethacc.uid " +
        filteredUids.fold("")(uids => s"AND ercop.ethereum_operation_uid IN ('${uids.mkString("','")}') ") +
        "ORDER BY ercop.date " + order.value +
        s" OFFSET $offset LIMIT $limit"
/*

SELECT ercop.uid, ercop.ethereum_operation_uid
FROM wallets w, erc20_operations ercop, erc20_accounts ercacc, ethereum_accounts ethacc
WHERE w.name='ethereum' AND ethacc.idx=0
AND ethacc.wallet_uid=w.uid AND ercop.account_uid = ercacc.uid AND ercacc.ethereum_account_uid=ethacc.uid  ;
 */
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
      queryEthereumOperations[PartialEthOperation](a.getIndex, w.getName, filteredUids, offset, limit) {
        row => {
          val opUid = row.get[String]("uid")
          uids = uids :+ opUid
          PartialEthOperation(
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
            row.get[String]("gas_price"),
            row.get[String]("gas_limit"),
            row.get[String]("gas_used"),
            BigInt(row.get[String]("status")),
            BigInt(row.get[String]("confirmations")),
            row.get[String]("senders"),
            row.get[String]("recipients")
          )
        }
      }
    }

    for {
      operations <- retrievePartialOperations
      // noneOperationUids = operations.filter(_.opType == "NONE").map(_.uid)
      // erc20 <- if (noneOperationUids.nonEmpty) findErc20FromUids(a, w, Some(noneOperationUids), 0, Int.MaxValue) else Seq.empty
      erc20 <- findErc20FromUids(a, w, Some(operations.map(_.uid)), 0, Int.MaxValue)
    } yield {
      operations.map(pop => {
        val blockView = (pop.blockHash, pop.blockHeight, pop.blockTime) match {
          case (Some(hash), Some(height), Some(time)) => Some(CommonBlockView(hash, height, time))
          case _ => None
        }
        val txView = EthereumTransactionView(blockView, pop.txHash, pop.recipient,
          pop.sender, pop.amount.toString, erc20.get(pop.uid), pop.gasPrice, pop.gasLimit, pop.date, pop.status.intValue())
        OperationView(pop.uid,
          currencyName, currencyFamily,
          None, pop.confirmations.longValue(),
          pop.date,
          pop.blockHeight,
          OperationType.valueOf(pop.opType),
          pop.amount.toString(),
          pop.fees.toString(),
          w.getName,
          a.getIndex,
          Seq(pop.sender),
          Seq(pop.recipient),
          Seq(pop.recipient).filter(a.getAccountKeychain.contains(_)), Some(txView))

      })
    }
  }

  /**
    * List operations from an account filtered by Uids
    * TODO : for a given eth op could we have several erc20 operations ?
    * If Yes Then we need to return Map[OperationUid, Seq[ERC20]]
    */
  def findErc20FromUids(a: Account, w: Wallet, filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int): Future[Map[OperationUid, EthereumTransactionView.ERC20]] = {
    logger.info(s"Retrieving erc20 operations for account : $a - limit=$limit offset=$offset filtered by ${filteredUids.map(_.size)}")

    def retrieveERC20Operations = {
      queryERC20Operations[(OperationUid, EthereumTransactionView.ERC20)](a.getIndex, w.getName, filteredUids, offset, limit) {
        row => {
          val ethOpUid = row.get[String]("eth_uid")
          val receiver = row.get[String]("receiver")
          val value = row.get[String]("value")
          (ethOpUid, EthereumTransactionView.ERC20(receiver, BigInt(value, 16)))
        }
      }
    }
    for {
      erc20 <- retrieveERC20Operations
    } yield erc20.toMap
  }

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
  def findOperationsByUids(a: Account, w: Wallet, filteredUids: Seq[OperationUid], offset: Int, limit: Int): Future[Seq[OperationView]] = {
    listOperationsByUids(a, w, Some(filteredUids), offset, limit)
  }

  private def queryEthereumOperations[T](accountIndex: Int, walletName: String, filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int)(f: Row => T) = {
    db.executeQuery[T](ethOperationQuery(accountIndex, walletName, Ordering.Ascending, filteredUids, offset, limit))(f)
  }
  private def queryERC20Operations[T](accountIndex: Int, walletName: String, filteredUids: Option[Seq[OperationUid]], offset: Int, limit: Int)(f: Row => T) = {
    db.executeQuery[T](erc20OperationQuery(accountIndex, walletName, Ordering.Ascending, filteredUids, offset, limit))(f)
  }
}
