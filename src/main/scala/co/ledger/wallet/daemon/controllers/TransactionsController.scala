package co.ledger.wallet.daemon.controllers

import co.ledger.core.{BitcoinLikePickingStrategy, RippleLikeMemo}
import co.ledger.wallet.daemon.controllers.requests.{CommonMethodValidations, RequestWithUser}
import co.ledger.wallet.daemon.models.coins.StellarMemo
import co.ledger.wallet.daemon.models.{AccountInfo, FeeMethod}
import co.ledger.wallet.daemon.services.TransactionsService
import co.ledger.wallet.daemon.utils.HexUtils
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import com.twitter.finatra.validation.{MethodValidation, ValidationResult}
import javax.inject.Inject

class TransactionsController @Inject()(transactionsService: TransactionsService) extends Controller {

  import TransactionsController._

  /**
    * Transaction creation method.
    * Input json
    * {
    * recipient: recipient address,
    * fees_per_byte: optional(in satoshi),
    * fees_level: optional(SLOW, FAST, NORMAL),
    * amount: in satoshi,
    * exclude_utxos: map{txHash: index}
    * partial_tx: optional(boolean) - lightweight mode can be used in order to calculate fees, partial mode avoid costly calls to retrieve unnecessary UTXOs info
    * }
    *
    */
  post("/pools/:pool_name/wallets/:wallet_name/accounts/:account_index/transactions") { request: AccountInfoRequest =>
    info(s"Create transaction $request: ${request.request.contentString}")
    transactionsService.createTransaction(request.request, request.accountInfo)
  }

  /**
    * Send a signed transaction.
    * Input json
    * {
    * raw_transaction: the bytes,
    * signatures: [string],
    * pubkeys: [string]
    * }
    */
  post("/pools/:pool_name/wallets/:wallet_name/accounts/:account_index/transactions/sign") { request: AccountInfoRequest =>
    info(s"Sign transaction $request: ${request.request.contentString}")
    transactionsService.broadcastTransaction(request.request, request.accountInfo)
  }

  /**
    * Serialize a signed transaction to its hash.
    * Input json
    * {
    * raw_transaction: the bytes,
    * signatures: [string],
    * pubkeys: [string]
    * }
    */
  post("/pools/:pool_name/wallets/:wallet_name/accounts/:account_index/transactions/hash") { request: AccountInfoRequest =>
    info(s"Sign transaction $request: ${request.request.contentString}")
    transactionsService.getTransactionHash(request.request, request.accountInfo)
  }

}

object TransactionsController {

  case class AccountInfoRequest(@RouteParam pool_name: String,
                                @RouteParam wallet_name: String,
                                @RouteParam account_index: Int,
                                request: Request) extends RequestWithUser {
    def accountInfo: AccountInfo = AccountInfo(account_index, wallet_name, pool_name, user.pubKey)
  }

  case class BroadcastBTCTransactionRequest(raw_transaction: String,
                                            signatures: Seq[String],
                                            pubkeys: Seq[String],
                                            request: Request) {
    def rawTx: Array[Byte] = HexUtils.valueOf(raw_transaction)

    def pairedSignatures: Seq[(Array[Byte], Array[Byte])] = signatures.zipWithIndex.map { case (sig, index) =>
      (HexUtils.valueOf(sig), HexUtils.valueOf(pubkeys(index)))
    }

    @MethodValidation
    def validateSignatures: ValidationResult = ValidationResult.validate(
      signatures.size == pubkeys.size,
      "signatures and pubkeys size not matching")
  }

  trait CreateTransactionRequest {
    def transactionInfo: TransactionInfo
  }

  // ETH, XRP and XLM
  case class BroadcastTransactionRequest(raw_transaction: String,
                                         signatures: Seq[String],
                                         request: Request) {
    def hexTx: Array[Byte] = HexUtils.valueOf(raw_transaction)

    def hexSig: Array[Byte] = HexUtils.valueOf(signatures.head)

    @MethodValidation
    def validateSignatures: ValidationResult = ValidationResult.validate(
      signatures.size == 1,
      s"expecting 1 DER signature, found ${signatures.size} instead."
    )
  }

  case class CreateETHTransactionRequest(recipient: String,
                                         amount: String,
                                         gas_limit: Option[String],
                                         gas_price: Option[String],
                                         fees_level: Option[String],
                                         contract: Option[String]
                                        ) extends CreateTransactionRequest {
    def amountValue: BigInt = BigInt(amount)

    def gasLimitValue: Option[BigInt] = gas_limit.map(BigInt(_))

    def gasPriceValue: Option[BigInt] = gas_price.map(BigInt(_))

    override def transactionInfo: TransactionInfo = ETHTransactionInfo(recipient, amountValue, gasLimitValue, gasPriceValue, fees_level, contract)

    @MethodValidation
    def validateFees: ValidationResult = CommonMethodValidations.validateFees(gasPriceValue, fees_level)
  }

  case class CreateBTCTransactionRequest(recipient: String,
                                         fees_per_byte: Option[String],
                                         fees_level: Option[String],
                                         amount: String,
                                         merge_utxo_strategy: Option[String],
                                         exclude_utxos: Option[Map[String, Int]],
                                         partial_tx: Option[Boolean]
                                        ) extends CreateTransactionRequest {
    def amountValue: BigInt = BigInt(amount)

    def feesPerByteValue: Option[BigInt] = fees_per_byte.map(BigInt(_))

    def transactionInfo: BTCTransactionInfo = BTCTransactionInfo(
      recipient, feesPerByteValue, fees_level, amountValue,
      merge_utxo_strategy.map(BitcoinLikePickingStrategy.valueOf).getOrElse(BitcoinLikePickingStrategy.OPTIMIZE_SIZE),
      exclude_utxos.getOrElse(Map[String, Int]()),
      partial_tx
    )

    @MethodValidation
    def validateFees: ValidationResult = CommonMethodValidations.validateFees(feesPerByteValue, fees_level)
  }

  case class XRPSendTo(amount: BigInt, address: String)

  case class XRPSendToRequest(amount: String, address: String)

  case class CreateXRPTransactionRequest(send_to: List[XRPSendToRequest], // Send funds to the given address.
                                         wipe_to: Option[String], // Send all available funds to the given address.
                                         fees: Option[String], // Fees (in drop) the originator is willing to pay
                                         fees_level: Option[String],
                                         memos: List[RippleLikeMemo], // Memos to add for this transaction
                                         destination_tag: Option[Long] // An arbitrary unsigned 32-bit integer that identifies a reason for payment or a non-Ripple account
                                        ) extends CreateTransactionRequest {
    def sendToValue: List[XRPSendTo] = send_to.map { r => XRPSendTo(BigInt(r.amount), r.address) }

    def feesValue: Option[BigInt] = fees.map(BigInt(_))

    override def transactionInfo: TransactionInfo = XRPTransactionInfo(sendToValue, wipe_to, feesValue: Option[BigInt], fees_level: Option[String], memos, destination_tag)

    @MethodValidation
    def validateFees: ValidationResult = CommonMethodValidations.validateFees(feesValue, fees_level)
  }

  case class XLMMemoRequest(`type`: String, value: String)

  case class CreateXLMTransactionRequest(recipient: String,
                                         amount: String,
                                         fees_amount: Option[String],
                                         fees_level: Option[String],
                                         memo: Option[XLMMemoRequest]) extends CreateTransactionRequest {
    def feesValue: Option[BigInt] = fees_amount.map(BigInt(_))

    override def transactionInfo: TransactionInfo =
      XLMTransactionInfo(
        recipient,
        BigInt(amount),
        feesValue,
        fees_level,
        memo.flatMap(m => StellarMemo.from(m.`type`, m.value))
      )

    @MethodValidation
    def validateMemo: ValidationResult = {
      memo match {
        case Some(XLMMemoRequest(memoType, memoValue)) =>
          StellarMemo.from(memoType, memoValue)
            .map(_ => ValidationResult.Valid())
            .getOrElse(ValidationResult.Invalid(s"Invalid memo with type=$memoType and value=$memoValue"))
        case None => ValidationResult.Valid()
      }
    }

    @MethodValidation
    def validateFees: ValidationResult = CommonMethodValidations.validateFees(feesValue, fees_level)
  }

  trait TransactionInfo

  case class BTCTransactionInfo(recipient: String,
                                fees: Option[BigInt],
                                feesLevel: Option[String],
                                amount: BigInt,
                                pickingStrategy: BitcoinLikePickingStrategy,
                                excludeUtxos: Map[String, Int],
                                partialTx: Option[Boolean]) extends TransactionInfo {
    lazy val feesSpeedLevel: Option[FeeMethod] = feesLevel.map { feesLevel => FeeMethod.from(feesLevel) }
  }

  case class ETHTransactionInfo(recipient: String,
                                amount: BigInt,
                                gasLimit: Option[BigInt],
                                gasPrice: Option[BigInt],
                                feesLevel: Option[String],
                                contract: Option[String]) extends TransactionInfo {
    lazy val feesSpeedLevel: Option[FeeMethod] = feesLevel.map { feesLevel => FeeMethod.from(feesLevel) }
  }

  case class XRPTransactionInfo(sendTo: List[XRPSendTo],
                                wipeTo: Option[String],
                                fees: Option[BigInt],
                                feesLevel: Option[String],
                                memos: List[RippleLikeMemo],
                                destinationTag: Option[Long]) extends TransactionInfo {
    lazy val feesSpeedLevel: Option[FeeMethod] = feesLevel.map { feesLevel => FeeMethod.from(feesLevel) }
  }

  case class XLMTransactionInfo(recipient: String,
                                amount: BigInt,
                                fees: Option[BigInt],
                                feesLevel: Option[String],
                                memo: Option[StellarMemo]) extends TransactionInfo {
    lazy val feesSpeedLevel: Option[FeeMethod] = feesLevel.map { level => FeeMethod.from(level) }
  }
}
