package co.ledger.wallet.daemon.models

import java.util.{Calendar, Date}

import cats.instances.future._
import cats.instances.list._
import cats.syntax.either._
import cats.syntax.traverse._
import co.ledger.core
import co.ledger.core._
import co.ledger.core.implicits.{InvalidEIP55FormatException, NotEnoughFundsException, UnsupportedOperationException, _}
import co.ledger.wallet.daemon.clients.ClientFactory
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.controllers.TransactionsController.{BTCTransactionInfo, ETHTransactionInfo, TransactionInfo, XRPTransactionInfo}
import co.ledger.wallet.daemon.exceptions.{ERC20BalanceNotEnough, ERC20NotFoundException, InvalidEIP55Format, SignatureSizeUnmatchException}
import co.ledger.wallet.daemon.libledger_core.async.LedgerCoreExecutionContext
import co.ledger.wallet.daemon.models.Currency._
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView
import co.ledger.wallet.daemon.models.coins.{Bitcoin, UnsignedEthereumTransactionView, UnsignedRippleTransactionView}
import co.ledger.wallet.daemon.schedulers.observers.{SynchronizationEventReceiver, SynchronizationResult}
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils.HexUtils
import co.ledger.wallet.daemon.utils.Utils.RichBigInt
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.primitives.UnsignedInteger
import com.twitter.inject.Logging

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}

object Account extends Logging {

  implicit class RichCoreAccount(val a: core.Account) extends AnyVal {
    def erc20Balance(contract: String)(implicit ec: ExecutionContext): Future[scala.BigInt] = {
      Account.erc20Balance(contract, a)
    }

    def erc20Balances(contracts: Option[Array[String]])(implicit ex: ExecutionContext): Future[Seq[scala.BigInt]] = {
      val contractList = (contracts.getOrElse(a.asEthereumLikeAccount().getERC20Accounts.asScala.toArray.map(ercAccount => ercAccount.getToken.getContractAddress)))
      a.asEthereumLikeAccount().getERC20Balances(contractList).map(_.asScala.map(coreBi => coreBi.asScala))
    }

    def erc20Operations(contract: String)(implicit ec: ExecutionContext): Future[List[(core.Operation, core.ERC20LikeOperation)]] =
      Account.erc20Operations(contract, a)

    def erc20Operations(implicit ec: ExecutionContext): Future[List[(core.Operation, core.ERC20LikeOperation)]] =
      Account.erc20Operations(a)

    def batchedErc20Operations(contract: String, limit: Int, batch: Int)(implicit ec: ExecutionContext): Future[List[(core.Operation, core.ERC20LikeOperation)]] =
      Account.batchedErc20Operations(contract, a, limit, batch)

    def batchedErc20Operations(limit: Int, batch: Int)(implicit ec: ExecutionContext): Future[List[(core.Operation, core.ERC20LikeOperation)]] =
      Account.batchedErc20Operations(a, limit, batch)

    def erc20Accounts: Either[Exception, List[core.ERC20LikeAccount]] =
      Account.erc20Accounts(a)

    def erc20Account(tokenAddress: String): Either[Exception, core.ERC20LikeAccount] =
      asERC20Account(tokenAddress, a)

    def getUtxo(offset: Int, batch: Int)(implicit ec: ExecutionContext): Future[List[co.ledger.core.BitcoinLikeOutput]] = {
      Account.getUtxo(offset, batch, a)
    }

    def getUtxoCount(): Future[Int] = {
      Account.getUtxoCount(a)
    }

    def balance(implicit ec: ExecutionContext): Future[scala.BigInt] =
      Account.balance(a)

    def balances(start: String, end: String, timePeriod: core.TimePeriod)(implicit ec: ExecutionContext): Future[List[scala.BigInt]] =
      Account.balances(start, end, timePeriod, a)

    def firstOperation(implicit ec: ExecutionContext): Future[Option[core.Operation]] =
      Account.firstOperation(a)

    def operationCounts(implicit ec: ExecutionContext): Future[Map[core.OperationType, Int]] =
      Account.operationCounts(a)

    def accountView(walletName: String, cv: CurrencyView)(implicit ec: ExecutionContext): Future[AccountView] =
      Account.accountView(walletName, cv, a)

    def broadcastBTCTransaction(rawTx: Array[Byte], signatures: Seq[BTCSigPub], currentHeight: Long, c: core.Currency): Future[String] =
      Account.broadcastBTCTransaction(rawTx, signatures, currentHeight, a, c)

    def broadcastETHTransaction(rawTx: Array[Byte], signatures: ETHSignature, c: core.Currency)(implicit ec: ExecutionContext): Future[String] =
      Account.broadcastETHTransaction(rawTx, signatures, a, c)

    def broadcastXRPTransaction(rawTx: Array[Byte], signatures: XRPSignature, c: core.Currency): Future[String] =
      Account.broadcastXRPTransaction(rawTx, signatures, a, c)

    def createTransaction(transactionInfo: TransactionInfo, c: core.Currency)(implicit ec: ExecutionContext): Future[TransactionView] =
      Account.createTransaction(transactionInfo, a, c)

    def operation(uid: String, fullOp: Int)(implicit ec: ExecutionContext): Future[Option[core.Operation]] =
      Account.operation(uid, fullOp, a)

    def operations(offset: Int, batch: Int, fullOp: Int)(implicit ec: ExecutionContext): Future[Seq[core.Operation]] =
      Account.operations(offset, batch, fullOp, a.queryOperations())

    def freshAddresses(implicit ec: ExecutionContext): Future[Seq[core.Address]] =
      Account.freshAddresses(a)

    def getAddressesInRange(from: Long, to: Long)(implicit ec: ExecutionContext): Future[Seq[core.Address]] =
      Account.getAddressesInRange(from, to, a)

    def sync(poolName: String, walletName: String)(implicit ec: ExecutionContext): Future[SynchronizationResult] =
      Account.sync(poolName, walletName, a)

    def startRealTimeObserver(): Unit =
      Account.startRealTimeObserver(a)

    def stopRealTimeObserver(): Unit =
      Account.stopRealTimeObserver(a)
  }

  def balance(a: core.Account)(implicit ex: ExecutionContext): Future[scala.BigInt] = a.getBalance().map { b =>
    debug(s"Account ${a.getIndex}, balance: $b")
    b.toBigInt.asScala
  }

  private def asETHAccount(a: core.Account): Either[Exception, EthereumLikeAccount] = {
    a.getWalletType match {
      case WalletType.ETHEREUM => Right(a.asEthereumLikeAccount())
      case _ => Left(new UnsupportedOperationException("current account is not an ETH account"))
    }
  }

  private def asERC20Account(contract: String, a: core.Account): Either[Exception, ERC20LikeAccount] = {
    asETHAccount(a).flatMap(_.getERC20Accounts.asScala.find(_.getToken.getContractAddress == contract) match {
      case Some(t) => Right(t)
      case None =>
        warn(s"Requested an erc20 account but it has not been found : $contract")
        Left(ERC20NotFoundException(contract))
    })
  }

  def getUtxo(offset: Int, batch: Int, a: core.Account)(implicit ex: ExecutionContext): Future[List[co.ledger.core.BitcoinLikeOutput]] = {
    a.asBitcoinLikeAccount().getUTXO(offset, offset + batch).map(_.asScala.toList)
  }

  def getUtxoCount(a: core.Account): Future[Int] = {
    a.asBitcoinLikeAccount().getUTXOCount()
  }

  def erc20Accounts(a: core.Account): Either[Exception, List[core.ERC20LikeAccount]] =
    asETHAccount(a).map(_.getERC20Accounts.asScala.toList)


  def erc20Balance(contract: String, a: core.Account)(implicit ec: ExecutionContext): Future[scala.BigInt] =
    for {
      account <- asERC20Account(contract, a).liftTo[Future]
      balance <- account.getBalance()
    } yield balance.asScala

  def erc20Operations(contract: String, a: core.Account)(implicit ec: ExecutionContext): Future[List[(core.Operation, core.ERC20LikeOperation)]] =
    asERC20Account(contract, a).liftTo[Future].flatMap(erc20Operations)

  def erc20Operations(a: core.Account)(implicit ec: ExecutionContext): Future[List[(core.Operation, core.ERC20LikeOperation)]] =
    for {
      account <- asETHAccount(a).liftTo[Future]
      ops <- account.getERC20Accounts.asScala.toList.flatTraverse(erc20Operations)
    } yield ops

  private def erc20Operations(a: core.ERC20LikeAccount)(implicit ec: ExecutionContext): Future[List[(core.Operation, core.ERC20LikeOperation)]] =
    a.queryOperations().complete().execute().map(_.asScala.toList).map { coreOps =>
      val hashToCoreOps = coreOps.map(coreOp => coreOp.asEthereumLikeOperation().getTransaction.getHash -> coreOp).toMap
      val erc20Ops = a.getOperations.asScala.toList
      erc20Ops.filter(erc20Op => {
        val opFound = hashToCoreOps.contains(erc20Op.getHash)
        if (!opFound) {
          error(s"Requested operation ${erc20Op.getHash} from account ${a.getAddress} has been skipped due to inconsistencies.")
        }
        opFound
      }
      ).map(erc20Op => (hashToCoreOps(erc20Op.getHash), erc20Op))
    }

  def batchedErc20Operations(contract: String, a: core.Account, offset: Int, batch: Int)(implicit ec: ExecutionContext): Future[List[(core.Operation, core.ERC20LikeOperation)]] =
    asERC20Account(contract, a).liftTo[Future].flatMap(erc20Acc => batchedErc20Operations(erc20Acc, offset, batch))

  def batchedErc20Operations(a: core.Account, offset: Int, batch: Int)(implicit ec: ExecutionContext): Future[List[(core.Operation, core.ERC20LikeOperation)]] =
    for {
      account <- asETHAccount(a).liftTo[Future]
      ops = account.getERC20Accounts.asScala.toList.map(erc20Account => batchedErc20Operations(erc20Account, offset, batch))
      result <- Future.sequence(ops).map(_.flatten)
    } yield result

  private def batchedErc20Operations(a: core.ERC20LikeAccount, offset: Int, batch: Int)(implicit ec: ExecutionContext): Future[List[(core.Operation, core.ERC20LikeOperation)]] =
    a.queryOperations().offset(offset).limit(batch).addOrder(OperationOrderKey.DATE, true).complete().execute().map(_.asScala.toList).map { coreOps =>
      val hashToERC20 = a.getOperations.asScala.toList.map(erc20Op => erc20Op.getHash -> erc20Op).toMap
      coreOps.map(coreOp => (coreOp, hashToERC20(coreOp.asEthereumLikeOperation().getTransaction.getHash)))
    }

  def operationCounts(a: core.Account)(implicit ex: ExecutionContext): Future[Map[core.OperationType, Int]] =
    a.queryOperations().addOrder(OperationOrderKey.DATE, true).partial().execute().map { os =>
      os.asScala.groupBy(o => o.getOperationType).map { case (optType, opts) => (optType, opts.size) }
    }

  def accountView(walletName: String, cv: CurrencyView, a: core.Account)(implicit ex: ExecutionContext): Future[AccountView] =
    for {
      b <- balance(a)
      opsCount <- operationCounts(a)
    } yield AccountView(walletName, a.getIndex, b, opsCount, a.getRestoreKey, cv)

  def broadcastBTCTransaction(rawTx: Array[Byte], signatures: Seq[BTCSigPub], currentHeight: Long, a: core.Account, c: core.Currency): Future[String] = {
    c.parseUnsignedBTCTransaction(rawTx, currentHeight) match {
      case Right(tx) =>
        if (tx.getInputs.size != signatures.size) Future.failed(SignatureSizeUnmatchException(tx.getInputs.size(), signatures.size))
        else {
          tx.getInputs.asScala.zipWithIndex.foreach { case (input, index) =>
            input.pushToScriptSig(c.concatSig(signatures(index)._1)) // signature
            input.pushToScriptSig(signatures(index)._2) // pubkey
          }
          debug(s"transaction after sign '${HexUtils.valueOf(tx.serialize())}'")
          a.asBitcoinLikeAccount().broadcastTransaction(tx)
        }
      case Left(m) => Future.failed(new UnsupportedOperationException(s"Account type not supported, can't broadcast BTC transaction: $m"))
    }
  }

  def broadcastETHTransaction(rawTx: Array[Byte], signature: ETHSignature, a: core.Account, c: core.Currency)(implicit ec: ExecutionContext): Future[String] = {
    c.parseUnsignedETHTransaction(rawTx) match {
      case Right(tx) =>
        // calculate the v from chain id
        val v: Long = c.getEthereumLikeNetworkParameters.getChainID.toLong * 2 + 35
        tx.setDERSignature(signature)
        tx.setVSignature(HexUtils.valueOf(v.toHexString))
        debug(s"transaction after sign '${HexUtils.valueOf(tx.serialize())}'")
        a.asEthereumLikeAccount().broadcastTransaction(tx).recoverWith {
          case _ =>
            tx.setVSignature(HexUtils.valueOf((v + 1).toHexString))
            debug(s"transaction after sign with V '${HexUtils.valueOf(tx.serialize())}'")
            a.asEthereumLikeAccount().broadcastTransaction(tx)
        }
      case Left(m) => Future.failed(new UnsupportedOperationException(s"Account type not supported, can't broadcast ETH transaction: $m"))
    }
  }

  def broadcastXRPTransaction(rawTx: Array[Byte], signature: XRPSignature, a: core.Account, c: core.Currency): Future[String] = {
    c.parseUnsignedXRPTransaction(rawTx) match {
      case Right(tx) =>
        tx.setDERSignature(signature)
        debug(s"transaction after sign '${HexUtils.valueOf(tx.serialize())}'")
        a.asRippleLikeAccount.broadcastTransaction(tx)

      case Left(m) => Future.failed(new UnsupportedOperationException(s"Account type not supported, can't broadcast XRP transaction: $m"))
    }
  }

  private def createBTCTransaction(ti: BTCTransactionInfo, a: core.Account, c: core.Currency)(implicit ec: ExecutionContext): Future[TransactionView] = {
    val partial: Boolean = ti.partialTx.getOrElse(false)
    for {
      // If fees are provided use it as it, else take the feeMethod to calculate it
      feesPerByte <- ti.fees match {
        case Some(amount) => Future.successful(amount)
        case None => ClientFactory.apiClient.getFees(c.getName).map(f => f.getAmount(ti.feesSpeedLevel.getOrElse(FeeMethod.NORMAL)))
      }
      tx <- a.asBitcoinLikeAccount().buildTransaction(partial)
        .sendToAddress(c.convertAmount(ti.amount), ti.recipient)
        .pickInputs(ti.pickingStrategy, UnsignedInteger.MAX_VALUE.intValue())
        .setFeesPerByte(c.convertAmount(feesPerByte))
        .build()
      v <- Bitcoin.newUnsignedTransactionView(tx, feesPerByte, partial)
    } yield v
  }

  private def createETHTransaction(ti: ETHTransactionInfo, a: core.Account, c: core.Currency)(implicit ec: ExecutionContext): Future[TransactionView] = {
    for {
      transactionBuilder <- ti.contract match {
        case Some(contract) => erc20TransactionBuilder(ti, a, c, contract)
        case None => ethereumTransactionBuilder(ti, a, c)
      }
      gasPrice <- ti.gasPrice match {
        case Some(gPrice) => Future.successful(gPrice)
        case None => ClientFactory.apiClient.getGasPrice(c.getName).map(ethInfo => ethInfo.getAmount(ti.feesSpeedLevel.getOrElse(FeeMethod.NORMAL)))
      }
      v <- transactionBuilder.setGasPrice(c.convertAmount(gasPrice)).build()
    } yield UnsignedEthereumTransactionView(v)
  }

  private def ethereumTransactionBuilder(ti: ETHTransactionInfo, a: core.Account, c: core.Currency)(implicit ec: ExecutionContext): Future[EthereumLikeTransactionBuilder] = {
    for {
      gasLimit <- ti.gasLimit match {
        case Some(amount) => Future.successful(amount)
        case None => ClientFactory.apiClient.getGasLimit(c, ti.contract.getOrElse(ti.recipient))
      }
    } yield {
      a.asEthereumLikeAccount()
        .buildTransaction()
        .setGasLimit(c.convertAmount(gasLimit))
        .sendToAddress(c.convertAmount(ti.amount), ti.recipient)
    }
  }

  private def erc20TransactionBuilder(ti: ETHTransactionInfo, a: core.Account, c: core.Currency, contract: String)(implicit ec: ExecutionContext): Future[EthereumLikeTransactionBuilder] = {
    a.asEthereumLikeAccount().getERC20Accounts.asScala.find(_.getToken.getContractAddress == contract) match {
      case Some(erc20Account) =>
        for {
          inputData <- erc20Account.getTransferToAddressData(BigInt.fromIntegerString(ti.amount.toString(10), 10), ti.recipient).recoverWith {
            case _: InvalidEIP55FormatException => Future.failed(InvalidEIP55Format(ti.recipient))
            case _: NotEnoughFundsException => Future.failed(ERC20BalanceNotEnough(contract, 0, ti.amount))
          }
          gasLimit <- ti.gasLimit match {
            case Some(amount) => Future.successful(amount)
            case None => ClientFactory.apiClient.getGasLimit(
              c, ti.contract.getOrElse(ti.recipient), Some(erc20Account.getAddress), Some(inputData))
          }
        } yield {
          a.asEthereumLikeAccount()
            .buildTransaction()
            .setGasLimit(c.convertAmount(gasLimit))
            .sendToAddress(c.convertAmount(0), contract)
            .setInputData(inputData)
        }
      case None => Future.failed(ERC20BalanceNotEnough(contract, 0, ti.amount))
    }
  }

  private def createXRPTransaction(ti: XRPTransactionInfo, a: core.Account, c: core.Currency)(implicit ec: ExecutionContext): Future[TransactionView] = {
    val builder = a.asRippleLikeAccount().buildTransaction()
    ti.sendTo.foreach(sendTo => builder.sendToAddress(c.convertAmount(sendTo.amount), sendTo.address))
    ti.wipeTo.foreach(builder.wipeToAddress)
    ti.memos.foreach(builder.addMemo)
    ti.destinationTag.foreach(builder.setDestinationTag)

    for {
      feeMethod <- Future.successful(ti.feesSpeedLevel.getOrElse(FeeMethod.NORMAL))
      fees <- ti.fees.fold(ClientFactory.apiClient.getFeesRipple.map(_.getAmount(feeMethod)))(Future.successful)
      _ = builder.setFees(c.convertAmount(fees))
      view <- builder.build().map(UnsignedRippleTransactionView.apply)
    } yield {
      info(s"Ripple Transaction created = $view Using fees method ($feeMethod) using network fees : ${ti.fees.isEmpty}")
      view
    }
  }

  def createTransaction(transactionInfo: TransactionInfo, a: core.Account, c: core.Currency)(implicit ec: ExecutionContext): Future[TransactionView] = {
    info(s"Creating transaction $transactionInfo")
    (transactionInfo, c.getWalletType) match {
      case (ti: BTCTransactionInfo, WalletType.BITCOIN) => createBTCTransaction(ti, a, c)
      case (ti: ETHTransactionInfo, WalletType.ETHEREUM) => createETHTransaction(ti, a, c)
      case (ti: XRPTransactionInfo, WalletType.RIPPLE) => createXRPTransaction(ti, a, c)
      case _ => Future.failed(new UnsupportedOperationException("Account type not supported, can't create transaction"))
    }
  }

  def operation(uid: String, fullOp: Int, a: core.Account)(implicit ec: ExecutionContext): Future[Option[core.Operation]] = {
    val q = a.queryOperations()
    q.filter().opAnd(core.QueryFilter.operationUidEq(uid))
    (if (fullOp > 0) q.complete().execute()
    else q.partial().execute()).map { ops =>
      debug(s"${ops.size()} returned with uid $uid")
      ops.asScala.headOption
    }
  }

  def firstOperation(a: core.Account)(implicit ec: ExecutionContext): Future[Option[core.Operation]] = {
    a.queryOperations().addOrder(OperationOrderKey.DATE, false).limit(1).partial().execute()
      .map { ops => ops.asScala.toList.headOption }
  }

  def operations(offset: Int, batch: Int, fullOp: Int, query: OperationQuery)(implicit ec: ExecutionContext): Future[Seq[core.Operation]] = {
    (if (fullOp > 0) {
      query.addOrder(OperationOrderKey.DATE, true).offset(offset).limit(batch).complete().execute()
    } else {
      query.addOrder(OperationOrderKey.DATE, true).offset(offset).limit(batch).partial().execute()
    }).map { operations => operations.asScala.toList }
  }

  def balances(start: String, end: String, timePeriod: core.TimePeriod, a: core.Account)(implicit ec: ExecutionContext): Future[List[scala.BigInt]] = {
    a.getBalanceHistory(start, end, timePeriod).map { balances =>
      balances.asScala.toList.map { ba => ba.toBigInt.asScala }
    }
  }

  @tailrec
  private def filter(start: Date, i: Int, end: Date, timePeriod: Int, operations: List[core.Operation], preResult: List[Map[core.OperationType, Int]]): List[Map[core.OperationType, Int]] = {
    def searchResult(condition: core.Operation => Boolean): Map[core.OperationType, Int] =
      operations.filter(condition).groupBy(op => op.getOperationType).map { case (optType, opts) => (optType, opts.size) }

    val (begin, next) = {
      val calendar = Calendar.getInstance()
      calendar.setTime(start)
      calendar.add(timePeriod, i - 1)
      val begin = calendar.getTime
      calendar.add(timePeriod, 1)
      (begin, calendar.getTime)
    }
    if (end.after(next)) {
      val result = searchResult(op => op.getDate.compareTo(begin) >= 0 && op.getDate.compareTo(next) < 0)
      filter(start, i + 1, end, timePeriod, operations, preResult ::: List(result))
    } else {
      val result = searchResult(op => op.getDate.compareTo(begin) >= 0 && op.getDate.compareTo(end) <= 0)
      preResult ::: List(result)
    }
  }

  def freshAddresses(a: core.Account)(implicit ec: ExecutionContext): Future[Seq[core.Address]] = {
    a.getFreshPublicAddresses().map(_.asScala.toList)
  }

  def getAddressesInRange(from: Long, to: Long, a: core.Account)(implicit ec: ExecutionContext): Future[Seq[core.Address]] = {
    if (a.isInstanceOfBitcoinLikeAccount) {
      a.asBitcoinLikeAccount.getAddresses(from, to).map(_.asScala.toList)
    } else {
      Future.failed(new UnsupportedOperationException(s"Account type ${a.getWalletType} not supported for get addresses in range."))
    }
  }

  def sync(poolName: String, walletName: String, a: core.Account)(implicit ec: ExecutionContext): Future[SynchronizationResult] = {
    val promise: Promise[SynchronizationResult] = Promise[SynchronizationResult]()
    val receiver: core.EventReceiver = new SynchronizationEventReceiver(a.getIndex, walletName, poolName, promise)
    a.synchronize().subscribe(LedgerCoreExecutionContext(ec), receiver)
    debug(s"Synchronize $a")
    val f = promise.future
    f onComplete (_ => a.getEventBus.unsubscribe(receiver))
    f
  }

  def startRealTimeObserver(a: core.Account): Unit = {
    if (DaemonConfiguration.realTimeObserverOn && !a.isObservingBlockchain) a.startBlockchainObservation()
    debug(LogMsgMaker.newInstance(s"Set real time observer on ${a.isObservingBlockchain}").append("account", a).toString())
  }

  def stopRealTimeObserver(a: core.Account): Unit = {
    debug(LogMsgMaker.newInstance("Stop real time observer").append("account", a).toString())
    if (a.isObservingBlockchain) a.stopBlockchainObservation()
  }


  class Derivation(private val accountCreationInfo: core.AccountCreationInfo) {
    val index: Int = accountCreationInfo.getIndex

    lazy val view: AccountDerivationView = {
      val paths = accountCreationInfo.getDerivations.asScala
      val owners = accountCreationInfo.getOwners.asScala
      val pubKeys = {
        val pks = accountCreationInfo.getPublicKeys
        if (pks.isEmpty) {
          paths.map { _ => "" }
        }
        else {
          pks.asScala.map(HexUtils.valueOf)
        }
      }
      val chainCodes = {
        val ccs = accountCreationInfo.getChainCodes
        if (ccs.isEmpty) {
          paths.map { _ => "" }
        }
        else {
          ccs.asScala.map(HexUtils.valueOf)
        }
      }
      val derivations = paths.indices.map { i =>
        DerivationView(
          paths(i),
          owners(i),
          pubKeys(i) match {
            case "" => None
            case pubKey => Option(pubKey)
          },
          chainCodes(i) match {
            case "" => None
            case chainCode => Option(chainCode)
          })
      }
      AccountDerivationView(index, derivations)
    }
  }

  class ExtendedDerivation(info: core.ExtendedKeyAccountCreationInfo) {
    val index: Int = info.getIndex
    lazy val view: AccountExtendedDerivationView = {
      val extKeys = info.getExtendedKeys.asScala.map(Option.apply).padTo(info.getDerivations.size(), None)
      val derivations = (info.getDerivations.asScala, info.getOwners.asScala, extKeys).zipped map {
        case (path, owner, key) =>
          ExtendedDerivationView(path, owner, key)
      }
      AccountExtendedDerivationView(index, derivations.toList)
    }
  }

  def newDerivation(coreD: core.AccountCreationInfo): Derivation = {
    new Derivation(coreD)
  }
}

case class AccountView(
                        @JsonProperty("wallet_name") walletName: String,
                        @JsonProperty("index") index: Int,
                        @JsonProperty("balance") balance: scala.BigInt,
                        @JsonProperty("operation_count") operationCounts: Map[core.OperationType, Int],
                        @JsonProperty("keychain") keychain: String,
                        @JsonProperty("currency") currency: CurrencyView
                      )

case class ERC20AccountView(
                             @JsonProperty("contract_address") contractAddress: String,
                             @JsonProperty("name") name: String,
                             @JsonProperty("number_of_decimal") numberOrDecimal: Int,
                             @JsonProperty("symbol") symbol: String,
                             @JsonProperty("balance") balance: scala.BigInt
                           )

object ERC20AccountView {
  def fromERC20Account(erc20Account: ERC20LikeAccount, bal: scala.BigInt): Future[ERC20AccountView] = {
    Future.successful(ERC20AccountView(
      erc20Account.getToken.getContractAddress,
      erc20Account.getToken.getName,
      erc20Account.getToken.getNumberOfDecimal,
      erc20Account.getToken.getSymbol,
      bal
    ))
  }
}

case class DerivationView(
                           @JsonProperty("path") path: String,
                           @JsonProperty("owner") owner: String,
                           @JsonProperty("pub_key") pubKey: Option[String],
                           @JsonProperty("chain_code") chainCode: Option[String]
                         )

case class AccountDerivationView(
                                  @JsonProperty("account_index") accountIndex: Int,
                                  @JsonProperty("derivations") derivations: Seq[DerivationView]
                                )

case class ExtendedDerivationView(
                                   @JsonProperty("path") path: String,
                                   @JsonProperty("owner") owner: String,
                                   @JsonProperty("extended_key") extKey: Option[String]
                                 )

case class AccountExtendedDerivationView(
                                          @JsonProperty("account_index") accountIndex: Int,
                                          @JsonProperty("derivations") derivations: Seq[ExtendedDerivationView]
                                        )

case class FreshAddressView(
                             @JsonProperty("address") address: String,
                             @JsonProperty("derivation_path") derivation: String
                           )

case class UTXOView(
                     @JsonProperty("hash") hash: String,
                     @JsonProperty("output_index") output_index: Long,
                     @JsonProperty("address") address: String,
                     @JsonProperty("height") height: Long,
                     @JsonProperty("confirmations") confirmations: Long,
                     @JsonProperty("amount") amount: scala.BigInt
                   )
