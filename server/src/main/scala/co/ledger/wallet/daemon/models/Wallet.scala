package co.ledger.wallet.daemon.models

import co.ledger.core
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.exceptions.CoreBadRequestException
import co.ledger.wallet.daemon.models.Account.{Derivation, ExtendedDerivation, _}
import co.ledger.wallet.daemon.models.Currency._
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils.HexUtils
import co.ledger.wallet.daemon.utils.Utils._
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.inject.Logging

import scala.collection.JavaConverters._
import scala.collection._
import scala.concurrent.{ExecutionContext, Future}

object Wallet extends Logging {

  implicit class RichCoreWallet(val w: core.Wallet) extends AnyVal {

    def lastBlockHeight(implicit ec: ExecutionContext): Future[Long] = Wallet.lastBlockHeight(w)

    def walletView(implicit ec: ExecutionContext): Future[WalletView] = Wallet.walletView(w)

    def accountDerivationPathInfo(index: Int)(implicit ec: ExecutionContext): Future[String] = Wallet.accountDerivationPathInfo(index, w)

    def account(index: Int)(implicit ec: ExecutionContext): Future[Option[core.Account]] = Wallet.account(index, w)

    def accountCreationInfo(index: Option[Int])(implicit ec: ExecutionContext): Future[Derivation] = Wallet.accountCreationInfo(index, w)

    def accountExtendedCreation(index: Option[Int])(implicit ec: ExecutionContext): Future[ExtendedDerivation] = Wallet.accountExtendedCreation(index, w)

    def accounts(implicit ec: ExecutionContext): Future[Seq[core.Account]] = Wallet.accounts(w)

    def addAccountIfNotExist(derivations: AccountExtendedDerivationView)
                            (implicit ec: ExecutionContext): Future[core.Account] =
      Wallet.addAccountIfNotExist(derivations, w)

    def addAccountIfNotExist(accountDerivations: AccountDerivationView)
                            (implicit ec: ExecutionContext): Future[core.Account] =
      Wallet.addAccountIfNotExist(accountDerivations, w)

    def syncAccounts(poolName: String)(implicit ec: ExecutionContext): Future[Seq[SynchronizationResult]] = Wallet.syncAccounts(poolName, w)
  }

  def lastBlockHeight(w: core.Wallet)(implicit ec: ExecutionContext): Future[Long] = {
    w.getLastBlock()
      .map(_.getHeight) recover {
      case _: co.ledger.core.implicits.BlockNotFoundException => 0
    }
  }

  def walletView(w: core.Wallet)(implicit ec: ExecutionContext): Future[WalletView] = {
    for {
      balance <- getBalance(w)
      ac <- w.getAccountCount()
    } yield WalletView(w.getName, ac, balance, w.getCurrency.currencyView, Map())
  }

  def accountDerivationPathInfo(index: Int, w: core.Wallet)(implicit ec: ExecutionContext): Future[String] = {
    w.getAccount(index).flatMap(_.getFreshPublicAddresses()).map(_.get(0).getDerivationPath)
  }

  def account(index: Int, w: core.Wallet)(implicit ec: ExecutionContext): Future[Option[core.Account]] = {
    w.getAccount(index).map { coreA =>
      Some(coreA)
    }.recover {
      case _: co.ledger.core.implicits.AccountNotFoundException => None
    }
  }

  def accountCreationInfo(index: Option[Int], w: core.Wallet)(implicit ec: ExecutionContext): Future[Derivation] = {
    index
      .map(w.getAccountCreationInfo(_))
      .getOrElse(w.getNextAccountCreationInfo())
      .map(Account.newDerivation)
  }

  def accountExtendedCreation(index: Option[Int], w: core.Wallet)(implicit ec: ExecutionContext): Future[ExtendedDerivation] =
    index
      .map(w.getExtendedKeyAccountCreationInfo(_))
      .getOrElse(w.getNextExtendedKeyAccountCreationInfo())
      .map(new ExtendedDerivation(_))

  def accounts(w: core.Wallet)(implicit ec: ExecutionContext): Future[Seq[core.Account]] = {
    for {
      count <- w.getAccountCount()
      accounts <- w.getAccounts(0, count)
    } yield accounts.asScala.map { a =>
      a
    }.toList
  }

  def addAccountIfNotExist(derivations: AccountExtendedDerivationView, w: core.Wallet)(implicit ec: ExecutionContext): Future[core.Account] = {
    val info = new core.ExtendedKeyAccountCreationInfo(
      derivations.accountIndex,
      derivations.derivations.map(_.owner).asArrayList,
      derivations.derivations.map(_.path).asArrayList,
      derivations.derivations.map(_.extKey.get).asArrayList
    )
    accountCreationEpilogue(w.newAccountWithExtendedKeyInfo(info), derivations.accountIndex, w)
  }

  def addAccountIfNotExist(accountDerivations: AccountDerivationView, w: core.Wallet)(implicit ec: ExecutionContext): Future[core.Account] = {
    val accountCreationInfo = new core.AccountCreationInfo(
      accountDerivations.accountIndex,
      accountDerivations.derivations.map(_.owner).asArrayList,
      accountDerivations.derivations.map(_.path).asArrayList,
      accountDerivations.derivations.map(d => HexUtils.valueOf(d.pubKey.get)).asArrayList,
      accountDerivations.derivations.map(d => HexUtils.valueOf(d.chainCode.get)).asArrayList
    )
    accountCreationEpilogue(w.newAccountWithInfo(accountCreationInfo), accountDerivations.accountIndex, w)
  }

  private def accountCreationEpilogue(coreAccount: Future[core.Account], accountIndex: Int, w: core.Wallet)
                                     (implicit ec: ExecutionContext): Future[core.Account] = {
    coreAccount.map { coreA =>
      info(LogMsgMaker.newInstance("Account created").append("index", coreA.getIndex).append("wallet_name", w.getName).toString())
      coreA
    }.recoverWith {
      case e: co.ledger.core.implicits.InvalidArgumentException =>
        Future.failed(CoreBadRequestException(e.getMessage, e))
      case e: co.ledger.core.implicits.AccountAlreadyExistsException =>
        for {
          _ <- Future(warn(LogMsgMaker.newInstance("Account already exist")
            .append("index", accountIndex).append("wallet_name", w.getName)
            .append("coreError", s"${'"'}${e.getMessage}${'"'}").toString()))
          a <- w.getAccount(accountIndex)
        } yield a
    }.map { coreA =>
      coreA
    }
  }

  def syncAccounts(poolName: String, w: core.Wallet)(implicit ec: ExecutionContext): Future[Seq[SynchronizationResult]] = {
    accounts(w).flatMap { accounts =>
      Future.sequence(accounts.map { account => account.sync(poolName, w.getName) })
    }
  }

  private def getBalance(w: core.Wallet)(implicit ec: ExecutionContext): Future[BigInt] = {
    accounts(w).flatMap { as =>
      Future.sequence(for (a <- as) yield a.balance).map { b => b.sum }
    }
  }
}

case class WalletView(
                       @JsonProperty("name") name: String,
                       @JsonProperty("account_count") accountCount: Int,
                       @JsonProperty("balance") balance: BigInt,
                       @JsonProperty("currency") currency: CurrencyView,
                       @JsonProperty("configuration") configuration: Map[String, Any]
                     )

case class WalletsViewWithCount(count: Int, wallets: Seq[WalletView])
