package co.ledger.wallet.daemon.services

import co.ledger.core.WalletType
import co.ledger.wallet.daemon.context.ApplicationContext.IOPool
import co.ledger.wallet.daemon.controllers.TransactionsController._
import co.ledger.wallet.daemon.database.DefaultDaemonCache
import co.ledger.wallet.daemon.exceptions.CurrencyNotFoundException
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.AccountInfo
import co.ledger.wallet.daemon.models.Wallet._
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.marshalling.MessageBodyManager
import javax.inject.{Inject, Singleton}

import scala.concurrent.Future

@Singleton
class TransactionsService @Inject()(defaultDaemonCache: DefaultDaemonCache, messageBodyManager: MessageBodyManager) extends DaemonService {

  def createTransaction(request: Request, accountInfo: AccountInfo): Future[TransactionView] = {
    defaultDaemonCache.withAccountAndWallet(accountInfo) {
      case (account, wallet) =>

        val transactionInfoEither = wallet.getWalletType match {
          case WalletType.BITCOIN => Right(messageBodyManager.read[CreateBTCTransactionRequest](request))
          case WalletType.ETHEREUM => Right(messageBodyManager.read[CreateETHTransactionRequest](request))
          case WalletType.RIPPLE => Right(messageBodyManager.read[CreateXRPTransactionRequest](request))
          case WalletType.STELLAR => Right(messageBodyManager.read[CreateXLMTransactionRequest](request))
          case w => Left(CurrencyNotFoundException(w.name()))
        }

        transactionInfoEither match {
          case Right(transactionInfo) =>
            account.createTransaction(transactionInfo.transactionInfo, wallet)
          case Left(t) => Future.failed(t)
        }
    }
  }

  def broadcastTransaction(request: Request, accountInfo: AccountInfo): Future[String] = {
    defaultDaemonCache.withAccountAndWallet(accountInfo) {
      case (account, wallet) =>
        for {
          currentHeight <- wallet.lastBlockHeight
          r <- wallet.getWalletType match {
            case WalletType.BITCOIN =>
              val req = messageBodyManager.read[BroadcastBTCTransactionRequest](request)
              account.broadcastBTCTransaction(req.rawTx, req.pairedSignatures, currentHeight, wallet.getCurrency)
            case WalletType.ETHEREUM =>
              val req = messageBodyManager.read[BroadcastTransactionRequest](request)
              account.broadcastETHTransaction(req.hexTx, req.hexSig, wallet.getCurrency)
            case WalletType.RIPPLE =>
              val req = messageBodyManager.read[BroadcastTransactionRequest](request)
              account.broadcastXRPTransaction(req.hexTx, req.hexSig, wallet.getCurrency)
            case WalletType.STELLAR =>
              val req = messageBodyManager.read[BroadcastTransactionRequest](request)
              account.broadcastXLMTransaction(req.hexTx, req.hexSig, wallet.getCurrency)
            case w => Future.failed(CurrencyNotFoundException(w.name()))
          }
        } yield r
    }
  }

  def getTransactionHash(request: Request, accountInfo: AccountInfo): Future[String] = {
    defaultDaemonCache.withAccountAndWallet(accountInfo) {
      case (account, wallet) =>
        for {
          r <- wallet.getWalletType match {
            case WalletType.RIPPLE =>
              val req = messageBodyManager.read[BroadcastTransactionRequest](request)
              account.getXRPTransactionHash(req.hexTx, req.hexSig, wallet.getCurrency)
            case w => Future.failed(CurrencyNotFoundException(w.name()))
          }
        } yield r
    }
  }
}
