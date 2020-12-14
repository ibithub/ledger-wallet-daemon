package co.ledger.wallet.daemon.utils

import com.typesafe.config.ConfigFactory

object FixturesUtils {

  val config = ConfigFactory.load("fixtures.conf")

  def seed(name: String) = config.getString(s"seeds.$name")

}
