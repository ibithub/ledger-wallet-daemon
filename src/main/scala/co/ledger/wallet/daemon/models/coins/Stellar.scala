package co.ledger.wallet.daemon.models.coins

import co.ledger.core.{Operation, StellarLikeMemoType, StellarLikeNetworkParameters, StellarLikeTransaction}
import co.ledger.wallet.daemon.controllers.TransactionsController.XLMTransactionInfo
import co.ledger.wallet.daemon.models.coins.Coin.{NetworkParamsView, TransactionView}
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.JavaConverters._
import scala.util.Try


case class StellarNetworkParamView(@JsonProperty("identifier") identifier: String,
                                   @JsonProperty("version") version: String,
                                   @JsonProperty("base_reserve") baseReserve: Long,
                                   @JsonProperty("base_fee") baseFee: Long,
                                   @JsonProperty("additional_seps") additionalSEPs: List[String],
                                   @JsonProperty("network_passphrase") NetworkPassphrase: String
                                  ) extends NetworkParamsView

object StellarNetworkParamView {
  def apply(n: StellarLikeNetworkParameters): StellarNetworkParamView =
    StellarNetworkParamView(
      n.getIdentifier,
      HexUtils.valueOf(n.getVersion),
      n.getBaseReserve,
      n.getBaseFee,
      n.getAdditionalSEPs.asScala.toList,
      n.getNetworkPassphrase
    )
}

trait StellarMemo {
  @JsonProperty("type")
  def memoType: StellarLikeMemoType
  @JsonProperty("value")
  def value: String
}

object StellarMemo {
  def from(memoType: String, value: String): Option[StellarMemo] = {
    Try(StellarLikeMemoType.valueOf(memoType)).toOption.flatMap {
      case StellarLikeMemoType.MEMO_TEXT => Some(StellarTextMemo(value))
      case StellarLikeMemoType.MEMO_ID => StellarNumberMemo.from(value)
      case StellarLikeMemoType.MEMO_HASH => StellarHashMemo.from(value)
      case StellarLikeMemoType.MEMO_RETURN => StellarReturnMemo.from(value)
      case StellarLikeMemoType.MEMO_NONE => None
    }
  }
}

final case class StellarTextMemo(value: String) extends StellarMemo {
  val memoType: StellarLikeMemoType = StellarLikeMemoType.MEMO_TEXT
}

final case class StellarNumberMemo(bigIntValue: BigInt) extends StellarMemo {
  val memoType: StellarLikeMemoType = StellarLikeMemoType.MEMO_ID
  val value: String = bigIntValue.toString()
}

object StellarNumberMemo {
  def from(value: String): Option[StellarNumberMemo] = {
    Try(BigInt(value)).map(StellarNumberMemo(_)).toOption
  }
}

final case class StellarHashMemo(byteArrayValue: Array[Byte]) extends StellarMemo {
  val memoType: StellarLikeMemoType = StellarLikeMemoType.MEMO_HASH
  val value: String = HexUtils.valueOf(byteArrayValue)
}

object StellarHashMemo {
  def from(value: String): Option[StellarHashMemo] = {
    Try(HexUtils.valueOf(value)).map(StellarHashMemo(_)).toOption
  }
}

final case class StellarReturnMemo(byteArrayValue: Array[Byte]) extends StellarMemo {
  val memoType: StellarLikeMemoType = StellarLikeMemoType.MEMO_RETURN
  val value: String = HexUtils.valueOf(byteArrayValue)
}

object StellarReturnMemo {
  def from(value: String): Option[StellarReturnMemo] = {
    Try(HexUtils.valueOf(value)).map(StellarReturnMemo(_)).toOption
  }
}

case class StellarTransactionView(@JsonProperty("hash") hash: String,
                                  @JsonProperty("sender") sender: String,
                                  @JsonProperty("receiver") receiver: String,
                                  @JsonProperty("value") value: String,
                                  @JsonProperty("fees") fees: String,
                                  @JsonProperty("memo") memo: Option[StellarMemo],
                                  @JsonProperty("is_successful") isSuccessful: Boolean) extends TransactionView

object StellarTransactionView {
  def apply(op: Operation): StellarTransactionView = {
    val stellarOperationRecord = op.asStellarLikeOperation().getRecord
    val stellarTransaction = op.asStellarLikeOperation().getTransaction
    val stellarMemo = stellarTransaction.getMemo

    StellarTransactionView(
      stellarOperationRecord.getTransactionHash,
      stellarTransaction.getSourceAccount.toString,
      op.getRecipients.asScala.head,
      op.getAmount.toString,
      stellarTransaction.getFee.toString,
      StellarMemo.from(stellarMemo.getMemoType.toString, stellarMemo.memoValuetoString()),
      stellarOperationRecord.getSuccessful
    )
  }
}

case class UnsignedStellarTransactionView(@JsonProperty("sender") sender: String,
                                          @JsonProperty("receiver") receiver: String,
                                          @JsonProperty("value") value: String,
                                          @JsonProperty("fees") fees: String,
                                          @JsonProperty("memo") memo: Option[StellarMemo],
                                          @JsonProperty("raw_transaction") rawTransaction: String) extends TransactionView

object UnsignedStellarTransactionView {
  def apply(tx: StellarLikeTransaction, ti: XLMTransactionInfo): UnsignedStellarTransactionView = {
    val stellarMemo = tx.getMemo
    UnsignedStellarTransactionView(
      ti.recipient,
      tx.getSourceAccount.toString,
      ti.amount.toString(),
      tx.getFee.toString,
      StellarMemo.from(stellarMemo.getMemoType.toString, stellarMemo.memoValuetoString()),
      HexUtils.valueOf(tx.toSignatureBase)
    )
  }
}
