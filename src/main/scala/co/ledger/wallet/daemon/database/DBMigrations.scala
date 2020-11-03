package co.ledger.wallet.daemon.database


/**
  * Object keeps database migration history
  */
object DBMigrations {
  import Tables._
  import Tables.profile.api._
  val Migrations = Map(
    0 -> (pools.schema ++ databaseVersions.schema).create
  )
}
