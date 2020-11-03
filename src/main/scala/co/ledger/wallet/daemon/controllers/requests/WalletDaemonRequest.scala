package co.ledger.wallet.daemon.controllers.requests

import co.ledger.wallet.daemon.models.{AccountInfo, PoolInfo, TokenAccountInfo, WalletInfo}
import com.twitter.finagle.http.Request

trait WalletDaemonRequest {
  def request: Request

  override def toString: String = s"$request"
}

trait WithTokenAccountInfo extends WithAccountInfo {
  self: WalletDaemonRequest =>

  def token_address: String
  def tokenAccountInfo: TokenAccountInfo = TokenAccountInfo(token_address, accountInfo)
}

trait WithWalletInfo extends WithPoolInfo {
  self: WalletDaemonRequest =>
  def wallet_name: String

  def walletInfo: WalletInfo = WalletInfo(wallet_name, poolInfo)
}

trait WithAccountInfo extends WithWalletInfo {
  self: WalletDaemonRequest =>
  def account_index: Int

  def accountInfo: AccountInfo = AccountInfo(account_index, walletInfo)
}

trait WithPoolInfo {
  self: WalletDaemonRequest =>
  def pool_name: String

  def poolInfo: PoolInfo = PoolInfo(pool_name)
}
