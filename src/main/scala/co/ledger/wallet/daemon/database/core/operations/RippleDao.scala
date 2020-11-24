package co.ledger.wallet.daemon.database.core.operations

import co.ledger.core.Wallet
import co.ledger.wallet.daemon.database.core.Database
import co.ledger.wallet.daemon.models.{AccountInfo, Operations}
import com.twitter.inject.Logging
import com.twitter.util.Future

class RippleDao(db: Database) extends CoinDao with Logging {
  logger.info(s"RippleDao created for ${db.client}")

  /**
    * List operations from an account
    */
  override def listAllOperations(a: AccountInfo, w: Wallet, offset: Int, limit: Int): Future[Seq[Operations.OperationView]] = {
    Future {
      Seq.empty
    }
  }

  /**
    * Find Operation
    */
  override def findOperationByUid(a: AccountInfo, w: Wallet, uid: OperationUid, offset: Int, limit: Int): Future[Option[Operations.OperationView]] = {
    Future {
      None
    }
  }

  /**
    * List operations from an account filtered by Uids
    */
  override def listOperationsByUids(a: AccountInfo, w: Wallet, filteredUids: Seq[OperationUid], offset: Int, limit: Int): Future[Seq[Operations.OperationView]] = {
    Future {
      Seq.empty
    }
  }
}
