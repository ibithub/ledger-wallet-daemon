package co.ledger.wallet.daemon.services

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.database._
import co.ledger.wallet.daemon.models.Wallet._
import co.ledger.wallet.daemon.models.{PoolInfo, WalletInfo, WalletView, WalletsViewWithCount}
import javax.inject.{Inject, Singleton}

import scala.concurrent.Future


@Singleton
class WalletsService @Inject()(daemonCache: DaemonCache) extends DaemonService {

  def wallets(offset: Int, bulkSize: Int, poolInfo: PoolInfo): Future[WalletsViewWithCount] = {
    daemonCache.getWallets(offset, bulkSize, poolInfo).flatMap { pair =>
      Future.sequence(pair._2.map(_.walletView)).map(WalletsViewWithCount(pair._1, _))
    }
  }

  def wallet(walletInfo: WalletInfo): Future[Option[WalletView]] = {
    daemonCache.getWallet(walletInfo).flatMap {
      case Some(wallet) => wallet.walletView.map(Option(_))
      case None => Future(None)
    }
  }

  def createWallet(currencyName: String, walletInfo: WalletInfo, isNativeSegwit: Boolean): Future[WalletView] = {
    daemonCache.createWallet(currencyName, walletInfo, isNativeSegwit).flatMap(_.walletView)
  }

}
