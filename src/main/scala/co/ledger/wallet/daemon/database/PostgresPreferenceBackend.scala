package co.ledger.wallet.daemon.database

import java.util

import co.ledger.core.{PreferencesBackend, PreferencesChange, PreferencesChangeType, RandomNumberGenerator}
import javax.inject.Inject
import slick.jdbc.JdbcBackend.Database
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import com.twitter.inject.Logging
import slick.jdbc.PostgresProfile

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

class PostgresPreferenceBackend @Inject() (db: Database) extends PreferencesBackend with Logging {
  val postgresTables = new Tables {
    override val profile = PostgresProfile
  }
  import postgresTables._
  import postgresTables.profile.api._

  def init(): Unit = {
    val f = db.run {
      preferences.schema.createIfNotExists
    }
    Await.result(f, 10.seconds)
  }

  override def get(bytes: Array[Byte]): Array[Byte] = {
    val f = db.run(
      preferences.filter(_.key === bytes).map(_.value).result.headOption
    ).map(_.orNull)
    Await.result(f, 10.seconds)
  }

  override def commit(arrayList: util.ArrayList[PreferencesChange]): Boolean = {
    if (!arrayList.isEmpty) {
      val updates = arrayList.asScala
        .filter(_.getType == PreferencesChangeType.PUT_TYPE)
        .map(o => PreferenceRow(o.getKey, o.getValue))
        .map(preferences.insertOrUpdate)
      val deletes = arrayList.asScala
        .filter(_.getType == PreferencesChangeType.DELETE_TYPE)
        .map(k => preferences.filter(_.key === k.getKey).delete)
      val action = DBIO.seq((updates ++ deletes): _*)
      val f = db.run(action).map(_ => true).recover {
        case e =>
          logger.error("failed to commit preference changes", e)
          false
      }
      Await.result(f, 10.seconds)
    } else true
  }

  override def setEncryption(randomNumberGenerator: RandomNumberGenerator, s: String): Unit = Unit

  override def unsetEncryption(): Unit = Unit

  override def resetEncryption(randomNumberGenerator: RandomNumberGenerator, s: String, s1: String): Boolean = true

  override def getEncryptionSalt: String = ""

  override def clear(): Unit = {
    val f = db.run(
      preferences.delete
    )
    Await.result(f, 10.seconds)
  }
}
