package co.ledger.wallet.daemon.models.coins

import java.util.Date

import co.ledger.core
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.models.coins.Coin._
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.JavaConverters._
import co.ledger.wallet.daemon.utils.Utils.RichBigInt

object Tezos {
  val currencyFamily = core.WalletType.TEZOS

  def newNetworkParamsView(from: core.TezosLikeNetworkParameters): NetworkParamsView = {
    TezosNetworkParamsView(
      from.getIdentifier,
      from.getMessagePrefix,
      HexUtils.valueOf(from.getXPUBVersion),
      HexUtils.valueOf(from.getImplicitPrefix),
      HexUtils.valueOf(from.getOriginatedPrefix),
      from.getAdditionalTIPs.asScala.toList,
      from.getTimestampDelay
    )
  }

  def newTransactionView(from: core.TezosLikeTransaction): TransactionView = {
    TezosTransactionView(
      from.getType,
      from.getHash,
      Option(from.getFees).map(fees => fees.toString),
      Option(from.getReceiver).map(recv => recv.toString),
      from.getSender.toString,
      from.getValue.toString,
      from.getDate,
      HexUtils.valueOf(from.getSigningPubKey),
      from.getCounter.asScala,
      from.getGasLimit.toBigInt.asScala,
      from.getStorageLimit.asScala,
      from.getBlockHash,
      from.getStatus
    )
  }

  // private def newBlockView(from: core.TezosLikeBlock): BlockView = {
  //   CommonBlockView(from.getHash, from.getHeight, from.getTime)
  // }
}

case class TezosNetworkParamsView(
                                     @JsonProperty("identifier") identifier: String,
                                     @JsonProperty("message_prefix") messagePrefix: String,
                                     @JsonProperty("xpub_version") xpubVersion: String,
                                     @JsonProperty("implicit_prefix") implicitPrefix: String,
                                     @JsonProperty("originated_prefix") originatedPrefix: String,
                                     @JsonProperty("additional_tips") additionalTips: List[String],
                                     @JsonProperty("timestamp_delay") timestampDelay: BigInt
                                   ) extends NetworkParamsView

object TezosNetworkParamsView {
  def apply(n: core.TezosLikeNetworkParameters): TezosNetworkParamsView =
    TezosNetworkParamsView(
      n.getIdentifier,
      n.getMessagePrefix,
      HexUtils.valueOf(n.getXPUBVersion),
      HexUtils.valueOf(n.getImplicitPrefix),
      HexUtils.valueOf(n.getOriginatedPrefix),
      n.getAdditionalTIPs.asScala.toList,
      n.getTimestampDelay
    )
}

case class TezosTransactionView(
                                   @JsonProperty("type") operationType: core.TezosOperationTag,
                                   @JsonProperty("hash") hash: String,
                                   @JsonProperty("fees") fees: Option[String],
                                   @JsonProperty("receiver") receiver: Option[String],
                                   @JsonProperty("sender") sender: String,
                                   @JsonProperty("value") value: String,
                                   @JsonProperty("date") date: Date,
                                   @JsonProperty("signing_pubkey") signing_pubkey: String,
                                   @JsonProperty("counter") counter: BigInt,
                                   @JsonProperty("gas_limit") gasLimit: BigInt,
                                   @JsonProperty("storage_limit") storageLimit: BigInt,
                                   @JsonProperty("block_hash") blockHash: String,
                                   @JsonProperty("status") status: Int
                                 ) extends TransactionView

object TezosTransactionView {
  def apply(op: core.Operation): TezosTransactionView = {
    val tx = op.asTezosLikeOperation().getTransaction

    TezosTransactionView(
      tx.getType,
      tx.getHash,
      Option(tx.getFees).map(fees => fees.toString),
      Option(tx.getReceiver).map(recv => recv.toString),
      tx.getSender.toString,
      tx.getValue.toString,
      tx.getDate,
      HexUtils.valueOf(tx.getSigningPubKey),
      tx.getCounter.asScala,
      tx.getGasLimit.toBigInt.asScala,
      tx.getStorageLimit.asScala,
      tx.getBlockHash,
      tx.getStatus
    )
  }
}

case class UnsignedTezosTransactionView(
                            @JsonProperty("raw_transaction") rawTransaction: String) extends TransactionView

object UnsignedTezosTransactionView {
  def apply(tx: core.TezosLikeTransaction): UnsignedTezosTransactionView = {
    UnsignedTezosTransactionView(
      HexUtils.valueOf(tx.serialize())
    )
  }
}
