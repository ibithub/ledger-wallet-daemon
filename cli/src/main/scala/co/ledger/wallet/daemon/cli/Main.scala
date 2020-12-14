package co.ledger.wallet.daemon.cli
import co.ledger.wallet.daemon.database.PoolDto
import co.ledger.wallet.daemon.models.Pool
import org.rogach.scallop._
import co.ledger.wallet.daemon.utils.NativeLibLoader


class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  val apples = opt[Int](required = true)
  val bananas = opt[Int]()
  val name = trailArg[String]()
  verify()
}

object Main {
  NativeLibLoader.loadLibs()

  def main(args: Array[String]) {
    val conf = new Conf(args)
    println("apples are: " + conf.apples())

    // 1 - Cmd generate sqlite database on sync
    // 2 - Cmd => Dump sqlite3 data on pgDb (temp tables)
    // Up Wallet Daemon in Maintenance mode (How?)
    // 3 -
    // a) Mode maintenance ON (based on env var) - do not load pools
    // b) Cmd Check WD is on debug mode
    // c) Cleanup operations (MO-1950)
    // d) Move from tmp tables to real tables (from to PG)
    // e) Update user-pref => libcore expose api for update
    // f) Remove sqlite temp db
  }
}
