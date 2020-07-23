package co.ledger.wallet.daemon.services

import co.ledger.core.WalletType
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
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
          case w => Left(CurrencyNotFoundException(w.name()))
        }

        transactionInfoEither match {
          case Right(transactionInfo) =>
            account.createTransaction(transactionInfo.transactionInfo, wallet.getCurrency)
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
              val req = messageBodyManager.read[BroadcastETHTransactionRequest](request)
              account.broadcastETHTransaction(req.hexTx, req.hexSig, wallet.getCurrency)
            case WalletType.RIPPLE =>
              val req = messageBodyManager.read[BroadcastXRPTransactionRequest](request)
              account.broadcastXRPTransaction(req.hexTx, req.hexSig, wallet.getCurrency)
            case w => Future.failed(CurrencyNotFoundException(w.name()))
          }
        } yield r
    }
  }

}
