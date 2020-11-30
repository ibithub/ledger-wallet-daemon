package co.ledger.wallet.daemon.database

import java.sql.Timestamp
import java.util.Date

import co.ledger.wallet.daemon.context.ApplicationContext.databaseEc
import co.ledger.wallet.daemon.database.DBMigrations.Migrations
import co.ledger.wallet.daemon.exceptions._
import com.twitter.inject.Logging
import javax.inject.{Inject, Singleton}
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.TransactionIsolation

import scala.concurrent.Future

@Singleton
class DatabaseDao @Inject()(db: Database) extends Logging {

  import Tables._
  import Tables.profile.api._


  def migrate(): Future[Unit] = {
    info("Start database migration")
    val lastMigrationVersion = databaseVersions.sortBy(_.version.desc).map(_.version).take(1).result.head
    db.run(lastMigrationVersion.transactionally) recover {
      case _ => -1
    } flatMap { currentVersion => {
      info(s"Current database version at $currentVersion")
      val maxVersion = Migrations.keys.toArray.sortWith(_ > _).head

      def migrate(version: Int, maxVersion: Int): Future[Unit] = {
        if (version > maxVersion) {
          info(s"Database version up to date at $maxVersion")
          Future.unit
        } else {
          info(s"Migrating version $version / $maxVersion")
          val rollbackMigrate = DBIO.seq(Migrations(version), insertDatabaseVersion(version))
          db.run(rollbackMigrate.transactionally).flatMap { _ =>
            info(s"version $version / $maxVersion migration done")
            migrate(version + 1, maxVersion)
          }
        }
      }

      migrate(currentVersion + 1, maxVersion)
    }
    }
  }

  def deletePool(poolName: String): Future[Option[PoolDto]] = {
    val query = filterPool(poolName)
    val action = for {
      result <- query.result.headOption
      _ <- query.delete
    } yield result
    db.run(action.withTransactionIsolation(TransactionIsolation.Serializable)).map { row =>
      row.map(createPool)
    }
  }

  def getAllPools: Future[Seq[PoolDto]] =
    safeRun(pools.sortBy(_.id.desc).result).map { rows => rows.map(createPool) }

  def getPoolByName(poolName: String): Future[Option[PoolDto]] =
    safeRun(pools.filter(pool => pool.name === poolName).result.headOption).map { row => row.map(createPool) }

  def insertPool(newPool: PoolDto): Future[Long] = {
    safeRun(filterPool(newPool.name).exists.result.flatMap { exists =>
      if (!exists) {
        pools.returning(pools.map(_.id)) += createPoolRow(newPool)
      } else {
        DBIO.failed(WalletPoolAlreadyExistException(newPool.name))
      }
    })
  }

  private def safeRun[R](query: DBIO[R]): Future[R] =
    db.run(query.transactionally).recoverWith {
      case e: DaemonException => Future.failed(e)
      case others: Throwable => Future.failed(DaemonDatabaseException("Failed to run database query", others))
    }

  private def createPoolRow(pool: PoolDto): PoolRow =
    PoolRow(0, pool.name, new Timestamp(new Date().getTime), pool.configuration, pool.dbBackend, pool.dbConnectString)

  private def createPool(poolRow: PoolRow): PoolDto =
    PoolDto(poolRow.name, poolRow.configuration, Option(poolRow.id), poolRow.dbBackend, poolRow.dbConnectString)

  private def insertDatabaseVersion(version: Int): DBIO[Int] =
    databaseVersions += (version, new Timestamp(new Date().getTime))

  private def filterPool(poolName: String) = {
    pools.filter(pool => pool.name === poolName.bind)
  }
}

case class PoolDto(name: String, configuration: String, id: Option[Long] = None, dbBackend: String = "", dbConnectString: String = "") {
  override def toString: String = "PoolDto(" +
    s"id: $id, " +
    s"name: $name, " +
    s"configuration: $configuration, " +
    s"dbBackend: $dbBackend, " +
    s"dbConnectString: $dbConnectString)"
}
