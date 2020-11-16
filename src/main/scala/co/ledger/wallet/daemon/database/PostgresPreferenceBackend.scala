package co.ledger.wallet.daemon.database

import java.nio.ByteBuffer
import java.util

import co.ledger.core.{PreferencesBackend, PreferencesChange, PreferencesChangeType, RandomNumberGenerator}
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.twitter.inject.Logging
import javax.inject.Inject
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

class PostgresPreferenceBackend @Inject()(db: Database) extends PreferencesBackend with Logging {
  type KeyPref = ByteBuffer
  type ValuePref = ByteBuffer
  val prefCache: LoadingCache[KeyPref, Option[ValuePref]] = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .build[KeyPref, Option[ValuePref]](new CacheLoader[KeyPref, Option[ValuePref]] {
      override def load(key: KeyPref): Option[ValuePref] = {
        loadPrefEntry(key.array()).map[ValuePref](ByteBuffer.wrap)
      }
    })

  val postgresTables: Tables = new Tables {
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
    prefCache.get(ByteBuffer.wrap(bytes)).map(_.array).orNull
  }

  private def loadPrefEntry(key: Array[Byte]): Option[Array[Byte]] = {
    val f = db.run(
      preferences.filter(_.key === key).map(_.value).result.headOption
    )
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

      val invalidation = f.andThen {
        case Success(_) => arrayList.forEach(p => prefCache.invalidate(ByteBuffer.wrap(p.getKey)))
      }
      Await.result(invalidation, 10.seconds)
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
