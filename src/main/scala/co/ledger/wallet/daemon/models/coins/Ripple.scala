package co.ledger.wallet.daemon.models.coins

import java.util.Date

import co.ledger.core._
import co.ledger.wallet.daemon.models.coins.Coin.{NetworkParamsView, TransactionView}
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.JavaConverters._

case class RippleNetworkParamView(@JsonProperty("identifier") identifier: String,
                                  @JsonProperty("message_prefix") messagePrefix: String,
                                  @JsonProperty("xpub_version") xpubVersion: String,
                                  @JsonProperty("additional_rips") additionalRIPs: List[String],
                                  @JsonProperty("timestamp_delay") timestampDelay: Long
                                 ) extends NetworkParamsView

object RippleNetworkParamView {
  def apply(n: RippleLikeNetworkParameters): RippleNetworkParamView =
    RippleNetworkParamView(
      n.getIdentifier,
      n.getMessagePrefix,
      HexUtils.valueOf(n.getXPUBVersion),
      n.getAdditionalRIPs.asScala.toList,
      n.getTimestampDelay
    )
}

case class RippleTransactionView(@JsonProperty("hash") hash: String,
                                 @JsonProperty("fees") fees: String,
                                 @JsonProperty("receiver") receiver: String,
                                 @JsonProperty("sender") sender: String,
                                 @JsonProperty("value") value: String,
                                 @JsonProperty("date") date: Date,
                                 @JsonProperty("status") status: Int,
                                 @JsonProperty("sequence") sequence: String,
                                 @JsonProperty("ledger_sequence") ledgerSequence: String,
                                 @JsonProperty("signing_pub_key") signingPubKey: String,
                                 @JsonProperty("memos") memos: List[RippleMemoView],
                                 @JsonProperty("destination_tag") destinationTag: Long
                                ) extends TransactionView

case class RippleMemoView(@JsonProperty("data") data: String,
                          @JsonProperty("fmt") fmt: String,
                          @JsonProperty("ty") ty: String)

object RippleTransactionView {
  def apply(tx: RippleLikeTransaction): RippleTransactionView = {
    RippleTransactionView(
      tx.getHash,
      tx.getFees.toString,
      tx.getReceiver.toBase58,
      tx.getSender.toBase58,
      tx.getValue.toString,
      tx.getDate,
      tx.getStatus,
      tx.getSequence.toString(10),
      tx.getLedgerSequence.toString(10),
      HexUtils.valueOf(tx.getSigningPubKey),
      tx.getMemos.asScala.toList.map(m => RippleMemoView(m.getData, m.getFmt, m.getTy)),
      tx.getDestinationTag
    )
  }
}

case class UnsignedRippleTransactionView(@JsonProperty("hash") hash: String,
                                         @JsonProperty("fees") fees: String,
                                         @JsonProperty("receiver") receiver: String,
                                         @JsonProperty("sender") sender: String,
                                         @JsonProperty("value") value: String,
                                         @JsonProperty("date") date: Date,
                                         @JsonProperty("status") status: Int,
                                         @JsonProperty("sequence") sequence: String,
                                         @JsonProperty("ledger_sequence") ledgerSequence: String,
                                         @JsonProperty("memos") memos: List[RippleLikeMemo],
                                         @JsonProperty("destination_tag") destinationTag: Long,
                                         @JsonProperty("raw_transaction") rawTransaction: String
                                        ) extends TransactionView

object UnsignedRippleTransactionView {
  def apply(tx: RippleLikeTransaction): UnsignedRippleTransactionView = {
    UnsignedRippleTransactionView(
      tx.getHash,
      tx.getFees.toString,
      tx.getReceiver.toBase58,
      tx.getSender.toBase58,
      tx.getValue.toString,
      tx.getDate,
      tx.getStatus,
      tx.getSequence.toString(10),
      tx.getLedgerSequence.toString(10),
      tx.getMemos.asScala.toList,
      tx.getDestinationTag,
      HexUtils.valueOf(tx.serialize)
    )
  }
}
