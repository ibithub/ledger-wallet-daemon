package co.ledger.wallet.daemon.database

import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.exceptions.{DaemonDatabaseException, UserAlreadyExistException}
import co.ledger.wallet.daemon.utils.HexUtils
import com.twitter.inject.Logging
import org.junit.Assert._
import org.junit.{BeforeClass, Test}
import org.scalatest.junit.AssertionsForJUnit
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class DatabaseDaoTest extends AssertionsForJUnit {
  import DatabaseDaoTest._
  @Test def verifyCreateUser(): Unit = {
    val pubKey = "A3B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    val expectedUser = UserDto(pubKey, 0)
    Await.result(dbDao.insertUser(expectedUser), Duration.Inf)
    val actualUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf).get
    assertEquals(expectedUser.pubKey, actualUser.pubKey)
    assertEquals(expectedUser.permissions, actualUser.permissions)
    assertNotNull(actualUser.id)
  }

  @Test def verifyCreateUsersWithSamePubKeyFailed(): Unit = {
    val pubKey = "03B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076F"
    val user1 = UserDto(pubKey, 0)
    val user2 = UserDto(pubKey, 1)
    Await.result(dbDao.insertUser(user1), Duration.Inf)
    try {
      Await.result(dbDao.insertUser(user2), Duration.Inf)
      fail()
    } catch {
      case _: UserAlreadyExistException => // Excepted
    }
  }

  @Test def verifyCreatePoolWithNonExistUser(): Unit = {
    val expectedPool = PoolDto("myPool", Long.MaxValue.longValue(), "")
    try {
      Await.result(dbDao.insertPool(expectedPool), Duration.Inf)
      fail()
    } catch {
      case _: DaemonDatabaseException => // user not exist
    }
  }

  @Test def verifyCreatePoolWithExistUser(): Unit = {
    val pubKey = "23B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    val id = Await.result(dbDao.insertUser(UserDto(pubKey, 0)), Duration.Inf)
    val insertedUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    assert(id === insertedUser.get.id.get)
    val expectedPool = PoolDto("myPool", insertedUser.get.id.get, "")
    Await.result(dbDao.insertPool(expectedPool), Duration.Inf)
    val actualPool =Await.result(dbDao.getPools(insertedUser.get.id.get), Duration.Inf)
    assertEquals(1, actualPool.size)
    assertEquals(expectedPool.name, actualPool.head.name)
    assertEquals(expectedPool.userId, actualPool.head.userId)
  }

  @Test def verifyReturnInsertedUserIds(): Unit = {
    val pubKey1 = "232AA94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    val id1 = Await.result(dbDao.insertUser(UserDto(pubKey1, 0)), Duration.Inf)
    val insertedUser1 = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey1)), Duration.Inf)
    assert(id1 === insertedUser1.get.id.get)
    val pubKey2 = "232AA94D8E33308DA08A3A8CA37D22101E212D85A2C0DFABC236A8C6A82E580D6A"
    val id2 = Await.result(dbDao.insertUser(UserDto(pubKey2, 0)), Duration.Inf)
    val insertedUser2 = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey2)), Duration.Inf)
    assert(id2 === insertedUser2.get.id.get)
    val pubKey3 = "922AA9DD8ED3308DD08A3A8CA378A2101E229D85A111DEEBC236A8C6A82E580D6A"
    val id3 = Await.result(dbDao.insertUser(UserDto(pubKey3, 0)), Duration.Inf)
    val insertedUser3 = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey3)), Duration.Inf)
    assert(id3 === insertedUser3.get.id.get)
  }

  @Test def verifyDeletePoolNotDeletingUserWithNoPool(): Unit = {
    val pubKey = "33B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    Await.result(dbDao.insertUser(UserDto(pubKey, 0)), Duration.Inf)
    val insertedUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    val poolId = Await.result(dbDao.insertPool(PoolDto("myPool", insertedUser.get.id.get, "")), Duration.Inf)
    val existingPools = Await.result(dbDao.getPools(insertedUser.get.id.get), Duration.Inf)
    assertEquals(1, existingPools.size)
    assertEquals(poolId, existingPools.head.id.get)
    assertEquals("myPool", existingPools.head.name)
    val deletedPool = Await.result(dbDao.deletePool("myPool", insertedUser.get.id.get), Duration.Inf)
    assertEquals(deletedPool, existingPools.headOption)
    val leftoverUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    assertFalse("User should not be deleted", leftoverUser.isEmpty)
    assertEquals(0, Await.result(dbDao.getPools(insertedUser.get.id.get), Duration.Inf).size)
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