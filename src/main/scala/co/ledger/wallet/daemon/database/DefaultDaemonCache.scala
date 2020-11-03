package co.ledger.wallet.daemon.database

import java.util.UUID

import co.ledger.wallet.daemon.context.ApplicationContext.IOPool
import co.ledger.wallet.daemon.exceptions.WalletPoolNotFoundException
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.Operations.PackedOperationsView
import co.ledger.wallet.daemon.models._
import com.twitter.inject.Logging
import javax.inject.Singleton

import scala.collection._
import scala.concurrent.Future

@Singleton
class DefaultDaemonCache() extends DaemonCache with Logging {

  import DefaultDaemonCache._

  def dbMigration: Future[Unit] = {
    dbDao.migrate()
  }

  def getAllPoolDtos: Future[Seq[PoolDto]] = dbDao.getAllPools

  def findPoolByName(name: String): Future[Option[PoolDto]] = dbDao.getPoolByName(name)

  def deletePool(poolName: String): Future[Unit] = {
    dbDao.deletePool(poolName).map {
      case Some(pool) => logger.info(s"Pool ${pool.name} has been deleted")
      case None => logger.warn(s"Pool $poolName has not been found. Deletion skipped.")
    }
  }

  def createWalletPool(poolInfo: PoolInfo, configuration: String): Future[Pool] = {
    val poolCreation = findPoolByName(poolInfo.poolName).map {
      case Some(dto) => Future.successful(dto)
      case None =>
        val dto = PoolDto(poolInfo.poolName, configuration)
        dbDao.insertPool(dto).map(id => dto.copy(id = Some(id)))
    }.flatten.map(dto => Pool.newPoolInstance(dto))
    poolCreation.map(_.getOrElse(throw WalletPoolNotFoundException(poolInfo.poolName)))
  }


  def getPreviousBatchAccountOperations(previous: UUID,
                                        fullOp: Int, accountInfo: AccountInfo): Future[PackedOperationsView] = {
    withAccountAndWallet(accountInfo) {
      case (account, wallet) =>
        val previousRecord = opsCache.getPreviousOperationRecord(previous)
        for {
          opsView <- account.operationViews(previousRecord.offset(), previousRecord.batch, fullOp, wallet)
        } yield PackedOperationsView(previousRecord.previous, previousRecord.next, opsView)
    }
  }

  def getNextBatchAccountOperations(next: UUID,
                                    fullOp: Int, accountInfo: AccountInfo): Future[PackedOperationsView] = {
    withAccountAndWalletAndPool(accountInfo) {
      case (account, wallet, pool) =>
        val candidate = opsCache.getOperationCandidate(next)
        for {
          opsView <- account.operationViews(candidate.offset(), candidate.batch, fullOp, wallet)
          realBatch = if (opsView.size < candidate.batch) opsView.size else candidate.batch
          next = if (realBatch < candidate.batch) None else candidate.next
          previous = candidate.previous
          operationRecord = opsCache.insertOperation(candidate.id, pool.id, accountInfo.walletName, accountInfo.accountIndex, candidate.offset(), candidate.batch, next, previous)
        } yield PackedOperationsView(operationRecord.previous, operationRecord.next, opsView)
    }
  }

  def getAccountOperations(batch: Int, fullOp: Int, accountInfo: AccountInfo): Future[PackedOperationsView] = {
    withAccountAndWalletAndPool(accountInfo) {
      case (account, wallet, pool) =>
        val offset = 0
        for {
          opsView <- account.operationViews(offset, batch, fullOp, wallet)
          realBatch = if (opsView.size < batch) opsView.size else batch
          next = if (realBatch < batch) None else Option(UUID.randomUUID())
          previous = None
          operationRecord = opsCache.insertOperation(UUID.randomUUID(), pool.id, accountInfo.walletName, accountInfo.accountIndex, offset, batch, next, previous)
        } yield PackedOperationsView(operationRecord.previous, operationRecord.next, opsView)
    }
  }
}

object DefaultDaemonCache extends Logging {
  private[database] val dbDao = new DatabaseDao(DatabaseInstance.instance)
  private[database] val opsCache: OperationCache = new OperationCache()
}
