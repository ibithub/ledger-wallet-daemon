package co.ledger.wallet.daemon.database

import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import com.twitter.inject.Logging
import org.junit.{BeforeClass, Test}
import org.scalatest.junit.AssertionsForJUnit
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class DatabaseDaoTest extends AssertionsForJUnit {

  import DatabaseDaoTest._

  @Test def verifyCreatePool(): Unit = {
    val poolName = "databasedaotest"
    val allPoolBefore = Await.result(dbDao.getAllPools, Duration.Inf)
    assert(!allPoolBefore.exists(_.name == poolName))

    val expectedPool = PoolDto(poolName, "")
    Await.result(dbDao.insertPool(expectedPool), Duration.Inf)
    val allPoolAfterCreation = Await.result(dbDao.getAllPools, Duration.Inf)
    assert(allPoolAfterCreation.exists(_.name == poolName))

    val deletedPool = Await.result(dbDao.deletePool(poolName), Duration.Inf)
    assert(deletedPool.isDefined)
    val allPoolAfterDeletion = Await.result(dbDao.getAllPools, Duration.Inf)
    assert(!allPoolAfterDeletion.exists(_.name == poolName))
  }
}

object DatabaseDaoTest extends Logging {
  @BeforeClass def initialization(): Unit = {
    debug("******************************* before class start")
    Await.result(dbDao.migrate(), Duration.Inf)
    debug("******************************* before class end")
  }

  private val dbDao = new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
}