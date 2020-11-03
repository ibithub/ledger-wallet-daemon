package co.ledger.wallet.daemon.database

import java.sql.Timestamp

import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.SqlType

object Tables extends Tables {
  override val profile = DaemonConfiguration.dbProfile
}

trait Tables {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._

  class DatabaseVersion(tag: Tag) extends Table[(Int, Timestamp)](tag, "__database__") {

    def version: Rep[Int] = column[Int]("version", O.PrimaryKey)

    def createdAt: Rep[Timestamp] = column[Timestamp]("created_at", SqlType("timestamp not null default CURRENT_TIMESTAMP"))

    override def * : ProvenShape[(Int, Timestamp)] = (version, createdAt)
  }

  val databaseVersions = TableQuery[DatabaseVersion]

  case class PoolRow(id: Long, name: String, createdAt: Timestamp, configuration: String, dbBackend: String, dbConnectString: String)

  class Pools(tag: Tag) extends Table[PoolRow](tag, "pools") {
    def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def createdAt: Rep[Timestamp] = column[Timestamp]("created_at", SqlType("timestamp default CURRENT_TIMESTAMP"))

    def configuration: Rep[String] = column[String]("configuration", O.Default("{}"))

    def dbBackend: Rep[String] = column[String]("db_backend")

    def dbConnectString: Rep[String] = column[String]("db_connect")

    def * : ProvenShape[PoolRow] = (id, name, createdAt, configuration, dbBackend, dbConnectString) <> (PoolRow.tupled, PoolRow.unapply)
  }

  val pools = TableQuery[Pools]

  case class PreferenceRow(key: Array[Byte], value: Array[Byte])
  class Preferences(tag: Tag) extends Table[PreferenceRow](tag, "preferences") {
    def key: Rep[Array[Byte]] = column[Array[Byte]]("key", O.PrimaryKey)
    def value: Rep[Array[Byte]] = column[Array[Byte]]("value")
    def * : ProvenShape[PreferenceRow] = (key, value) <> (PreferenceRow.tupled, PreferenceRow.unapply)
  }
  val preferences = TableQuery[Preferences]
}
