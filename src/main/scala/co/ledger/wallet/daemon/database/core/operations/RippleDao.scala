package co.ledger.wallet.daemon.database.core.operations

import co.ledger.core.{Account, Wallet}
import co.ledger.wallet.daemon.database.core.Database
import co.ledger.wallet.daemon.models.Operations
import com.twitter.inject.Logging
import com.twitter.util.Future

class RippleDao(db: Database) extends CoinDao with Logging {
  logger.info(s"RippleDao created for ${db.client}")

  /**
    * List operations from an account
    */
  override def listAllOperations(a: Account, w: Wallet, offset: Int, limit: Int): Future[Seq[Operations.OperationView]] = {
    Future {
      Seq.empty
    }
  }

  /**
    * Find Operation
    */
  override def findOperationByUid(a: Account, w: Wallet, uid: OperationUid, offset: Int, limit: Int): Future[Option[Operations.OperationView]] = {
    Future {
      None
    }
  }

  /**
    * List operations from an account filtered by Uids
    */
  override def findOperationsByUids(a: Account, w: Wallet, filteredUids: Seq[OperationUid], offset: Int, limit: Int): Future[Seq[Operations.OperationView]] = {
    Future {
      Seq.empty
    }
  }
}
