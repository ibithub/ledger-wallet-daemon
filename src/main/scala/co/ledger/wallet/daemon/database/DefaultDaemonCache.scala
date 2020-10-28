package co.ledger.wallet.daemon.database

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.Operations.PackedOperationsView
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.schedulers.observers.{NewOperationEventReceiver, SynchronizationResult}
import co.ledger.wallet.daemon.services.LogMsgMaker
import com.twitter.inject.Logging
import javax.inject.Singleton
import slick.jdbc.JdbcBackend.Database

import scala.collection.JavaConverters._
import scala.collection._
import scala.concurrent.Future

@Singleton
class DefaultDaemonCache() extends DaemonCache with Logging {

  import DefaultDaemonCache._

  def dbMigration: Future[Unit] = {
    dbDao.migrate()
  }

  def syncOperations(): Future[Seq[SynchronizationResult]] = {
    getUsers.flatMap { us =>
      Future.sequence(us.map { user => user.sync() }).map(_.flatten)
    }
  }

  def getUser(pubKey: String): Future[Option[User]] = {
    if (users.contains(pubKey)) {
      Future.successful(users.get(pubKey))
    }
    else {
      dbDao.getUser(pubKey).map { dto =>
        dto.map(user => users.put(pubKey, newUser(user)))
      }.map { _ => users.get(pubKey) }
    }
  }

  def getUsers: Future[Seq[User]] = {
    dbDao.getUsers.map { us =>
      us.map { user =>
        if (!users.contains(user.pubKey)) users.put(user.pubKey, newUser(user))
        users(user.pubKey)
      }
    }
  }

  def createUser(pubKey: String, permissions: Int): Future[User] = {
    val user = UserDto(pubKey, permissions)
    dbDao.insertUser(user).map { id =>
      val u = new User(id, user.pubKey)
      users.put(user.pubKey, u)
      info(LogMsgMaker.newInstance("User created").append("user", users(user.pubKey)).toString())
      u
    }
  }

  def getPreviousBatchAccountOperations(previous: UUID,
                                        fullOp: Int, accountInfo: AccountInfo): Future[PackedOperationsView] = {
    withAccountAndWallet(accountInfo) {
      case (account, wallet) =>
        val previousRecord = opsCache.getPreviousOperationRecord(previous)
        for {
          ops <- account.operations(previousRecord.offset(), previousRecord.batch, fullOp)
          opsView <- Future.sequence(ops.map { op => Operations.getView(op, wallet, account) })
        } yield PackedOperationsView(previousRecord.previous, previousRecord.next, opsView)
    }
  }

  def getNextBatchAccountOperations(next: UUID,
                                    fullOp: Int, accountInfo: AccountInfo): Future[PackedOperationsView] = {
    withAccountAndWalletAndPool(accountInfo) {
      case (account, wallet, pool) =>
        val candidate = opsCache.getOperationCandidate(next)
        for {
          ops <- account.operations(candidate.offset(), candidate.batch, fullOp)
          realBatch = if (ops.size < candidate.batch) ops.size else candidate.batch
          next = if (realBatch < candidate.batch) None else candidate.next
          previous = candidate.previous
          operationRecord = opsCache.insertOperation(candidate.id, pool.id, accountInfo.walletName, accountInfo.accountIndex, candidate.offset(), candidate.batch, next, previous)
          opsView <- Future.sequence(ops.map { op => Operations.getView(op, wallet, account) })
        } yield PackedOperationsView(operationRecord.previous, operationRecord.next, opsView)
    }
  }

  def getAccountOperations(batch: Int, fullOp: Int, accountInfo: AccountInfo): Future[PackedOperationsView] = {
    withAccountAndWalletAndPool(accountInfo) {
      case (account, wallet, pool) =>
        val offset = 0
        for {
          ops <- account.operations(offset, batch, fullOp)
          _ = ops.map(op => debug(s"OPTYPE=${op.getOperationType}"))
          realBatch = if (ops.size < batch) ops.size else batch
          next = if (realBatch < batch) None else Option(UUID.randomUUID())
          previous = None
          operationRecord = opsCache.insertOperation(UUID.randomUUID(), pool.id, accountInfo.walletName, accountInfo.accountIndex, offset, batch, next, previous)
          opsView <- Future.sequence(ops.map { op => Operations.getView(op, wallet, account) })
        } yield PackedOperationsView(operationRecord.previous, operationRecord.next, opsView)
    }
  }
}

object DefaultDaemonCache extends Logging {

  def newUser(user: UserDto): User = {
    assert(user.id.isDefined, "User id must exist")
    new User(user.id.get, user.pubKey)
  }

  private[database] val dbDao = new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
  private[database] val opsCache: OperationCache = new OperationCache()
  private val users: concurrent.Map[String, User] = new ConcurrentHashMap[String, User]().asScala

  class User(val id: Long, val pubKey: String) extends Logging {
    private[this] val cachedPools: concurrent.Map[String, Pool] = new ConcurrentHashMap[String, Pool]().asScala
    private[this] val self = this

    def sync(): Future[Seq[SynchronizationResult]] = {
      pools().flatMap { pls =>
        Future.sequence(pls.map { p =>
          p.sync()
        }).map(_.flatten)
      }
    }

    /**
      * Delete pool will:
      *  1. remove the pool from daemon database
      *  2. unsubscribe event receivers to core library, see details on method `clear` from `Pool`
      *  3. remove the operations were done on this pool, which includes all underlying wallets and accounts
      *  4. remove the pool from cache.
      *
      * @param name the name of wallet pool needs to be deleted.
      * @return a Future of Unit.
      */
    def deletePool(name: String): Future[Unit] = {
      dbDao.deletePool(name, id).flatMap { deletedPool =>
        if (deletedPool.isDefined) opsCache.deleteOperations(deletedPool.get.id.get)
        clearCache(name).map { _ =>
          info(LogMsgMaker.newInstance("Pool deleted").append("name", name).append("user_id", id).toString())
        }
      }
    }

    private def clearCache(poolName: String): Future[Option[Pool]] = Future {
      cachedPools.remove(poolName)
    }

    def addPoolIfNotExit(name: String, configuration: String): Future[Pool] = {
      val dto = PoolDto(name, id, configuration)
      dbDao.insertPool(dto).flatMap { poolId =>
        toCache(dto, poolId).map { pool =>
          info(LogMsgMaker.newInstance("Pool created").append("name", name).append("user_id", id).toString())
          pool
        }
      }.recoverWith {
        case _: WalletPoolAlreadyExistException =>
          warn(LogMsgMaker.newInstance("Pool already exist").append("name", name).append("user_id", id).toString())
          pool(name).map(op => op.getOrElse(throw WalletPoolNotFoundException(s"Failed to retrieve pool, inconsistent state for pool $name")))

      }
    }

    /**
      * Getter for individual pool with specified name. This method will perform a daemon database search in order
      * to get the most up to date information. If specified pool doesn't exist in database but in cache. The cached
      * pool will be cleared. See `clear` method from Pool for detailed actions.
      *
      * @param name the name of wallet pool.
      * @return a Future of `co.ledger.wallet.daemon.models.Pool` instance Option.
      */
    def pool(name: String): Future[Option[Pool]] = {
      dbDao.getPool(id, name).flatMap {
        case Some(p) => toCache(p, p.id.get).map(Option(_))
        case None => clearCache(name).map { _ => None }
      }
    }

    private def toCache(p: PoolDto, poolId: Long): Future[Pool] = {
      cachedPools.get(p.name) match {
        case Some(pool) => Future.successful(pool)
        case None => Pool.newCoreInstance(p).flatMap { coreP =>
          cachedPools.put(p.name, Pool.newInstance(coreP, poolId))
          debug(s"Add ${cachedPools(p.name)} to $self cache")
          cachedPools.get(p.name).foreach(p => p.registerEventReceiver(new NewOperationEventReceiver(poolId, opsCache)))
          cachedPools.get(p.name) match {
            case None => Future.failed(new WalletPoolNotFoundException(p.name))
            case Some(pool) => Future.successful(pool)
          }
        }
      }
    }

    /**
      * Obtain available pools of this user. The method performs database call(s), adds the missing
      * pools to cache.
      *
      * @return the resulting pools. The result may contain less entities
      *         than the cached entities.
      */
    def pools(): Future[Seq[Pool]] = for {
      poolDtos <- dbDao.getPools(id)
      pools <- Future.sequence(poolDtos.map { pool => toCache(pool, pool.id.get) })
    } yield pools


    override def toString: String = s"User(id: $id, pubKey: $pubKey)"

  }

}
