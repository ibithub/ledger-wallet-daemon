package co.ledger.wallet.daemon.models

case class TokenAccountInfo(tokenAddress: String, accountInfo: AccountInfo)

case class AccountInfo(accountIndex: Int, walletInfo: WalletInfo) {
  def poolName: String = walletInfo.poolInfo.poolName
  def walletName: String = walletInfo.walletName
}

object AccountInfo {
  def apply(accountIndex: Int, walletName: String, poolName: String, pubKey: String): AccountInfo =
    apply(accountIndex, WalletInfo(walletName, poolName, pubKey))
}

case class WalletInfo(walletName: String, poolInfo: PoolInfo)

object WalletInfo {
  def apply(walletName: String, poolName: String, pubKey: String): WalletInfo =
    apply(walletName, PoolInfo(poolName, pubKey))
}

case class PoolInfo(poolName: String, pubKey: String)
