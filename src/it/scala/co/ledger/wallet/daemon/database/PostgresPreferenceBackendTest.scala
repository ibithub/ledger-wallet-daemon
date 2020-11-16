package co.ledger.wallet.daemon.database

import java.util

import co.ledger.core.{PreferencesChange, PreferencesChangeType}
import com.twitter.inject.Logging
import com.typesafe.config.ConfigFactory
import org.junit.{BeforeClass, Test}
import org.scalatest.junit.AssertionsForJUnit
import slick.jdbc.JdbcBackend.Database

class PostgresPreferenceBackendTest extends Logging with AssertionsForJUnit{
  import PostgresPreferenceBackendTest._

  @Test def saveReadDeletePreference(): Unit = {
    val key = Array(1.toByte)
    val value = Array(2.toByte)
    val insertPreference = new PreferencesChange(PreferencesChangeType.PUT_TYPE, key, value)
    val params = new util.ArrayList[PreferencesChange]()
    params.add(insertPreference)
    info("committing preference to database")
    preferenceBackend.commit(params)
    info("reading preference from database")
    verifyGet(key, value)
    info("deleting preference from database")
    val deletePreference = new PreferencesChange(PreferencesChangeType.DELETE_TYPE, key, value)
    params.clear()
    params.add(deletePreference)
    preferenceBackend.commit(params)
    verifyDelete(key)
  }

  @Test def batchInsert(): Unit = {
    val keyValues: Array[(Array[Byte], Array[Byte])] = Array(
      ("key1".toCharArray.map(_.toByte), "value1".toCharArray.map(_.toByte)),
      ("key2".toCharArray.map(_.toByte), "value2".toCharArray.map(_.toByte)),
      ("key3".toCharArray.map(_.toByte), "value3".toCharArray.map(_.toByte)),
      ("key4".toCharArray.map(_.toByte), "value4".toCharArray.map(_.toByte)),
      ("key5".toCharArray.map(_.toByte), "value5".toCharArray.map(_.toByte)),
      ("key6".toCharArray.map(_.toByte), "value6".toCharArray.map(_.toByte)),
      ("key7".toCharArray.map(_.toByte), "value7".toCharArray.map(_.toByte)),
    )
    val params = new util.ArrayList[PreferencesChange]()
    keyValues.foreach{
      case (key, value) =>
        val p = new PreferencesChange(PreferencesChangeType.PUT_TYPE, key, value)
        params.add(p)
    }
    preferenceBackend.commit(params)
    keyValues.foreach {
      case (key, value) => verifyGet(key, value)
    }
  }

  private def verifyGet(key: Array[Byte], value: Array[Byte]) = {
    val result = preferenceBackend.get(key)
    assert(result.sameElements(value), "value get from database should be the same as the one in database")
  }

  private def verifyDelete(key: Array[Byte]) = {
    val result = preferenceBackend.get(key)
    assert(result == null, "get non existing key should return null")
  }

}

object PostgresPreferenceBackendTest extends Logging {
  private val config = ConfigFactory.load()
  private val dbPort = config.getString("postgres.port")
  private val dbHost = config.getString("postgres.host")
  private val dbUserName = config.getString("postgres.username")
  private val dbPwd = config.getString("postgres.password")
  private val dbPrefix = config.getString("postgres.db_name_prefix")
  private val dbName = dbPrefix + "preferenceBackendTest"

  private val db = Database.forURL(
    s"jdbc:postgresql://$dbHost:$dbPort/$dbName?user=$dbUserName&password=$dbPwd"
  )
  private val preferenceBackend = new PostgresPreferenceBackend(db)

  @BeforeClass def initialization(): Unit = {
    preferenceBackend.init()
  }
}
