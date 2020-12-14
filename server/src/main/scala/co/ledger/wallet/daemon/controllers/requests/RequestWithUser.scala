package co.ledger.wallet.daemon.controllers.requests

import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import co.ledger.wallet.daemon.models.{AccountInfo, PoolInfo, TokenAccountInfo, WalletInfo}
import co.ledger.wallet.daemon.filters.AuthentifiedUserContext._
import com.twitter.finagle.http.Request

trait RequestWithUser {
  def request: Request

  def user: User = request.user.get

  override def toString: String = s"$request, Parameters(user: ${user.id})"
}

trait WithTokenAccountInfo extends WithAccountInfo {
  self: RequestWithUser =>

  def token_address: String
  def tokenAccountInfo: TokenAccountInfo = TokenAccountInfo(token_address, accountInfo)
}

trait WithWalletInfo extends WithPoolInfo {
  self: RequestWithUser =>
  def wallet_name: String

  def walletInfo: WalletInfo = WalletInfo(wallet_name, poolInfo)
}

trait WithAccountInfo extends WithWalletInfo {
  self: RequestWithUser =>
  def account_index: Int

  def accountInfo: AccountInfo = AccountInfo(account_index, walletInfo)
}

trait WithPoolInfo {
  self: RequestWithUser =>
  def pool_name: String

  def poolInfo: PoolInfo = PoolInfo(pool_name, user.pubKey)
}
