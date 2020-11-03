package co.ledger.wallet.daemon.database

import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import slick.jdbc.JdbcBackend.Database

object DatabaseInstance {
  val instance = Database.forConfig(DaemonConfiguration.dbProfileName)
}
