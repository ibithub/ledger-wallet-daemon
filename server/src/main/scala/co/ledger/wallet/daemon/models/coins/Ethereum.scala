package co.ledger.wallet.daemon.models.coins

import java.util.Date

import co.ledger.core._
import co.ledger.wallet.daemon.models.coins.Coin.{NetworkParamsView, TransactionView}
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty
import co.ledger.wallet.daemon.utils.Utils.RichBigInt

import scala.collection.JavaConverters._

case class EthereumNetworkParamView(
                                     @JsonProperty("identifier") identifier: String,
                                     @JsonProperty("message_prefix") messagePrefix: String,
                                     @JsonProperty("xpub_version") xpubVersion: String,
                                     @JsonProperty("additional_eips") additionalEIPs: List[String],
                                     @JsonProperty("timestamp_delay") timestampDelay: Long
                                   ) extends NetworkParamsView

object EthereumNetworkParamView {
  def apply(n: EthereumLikeNetworkParameters): EthereumNetworkParamView =
    EthereumNetworkParamView(
      n.getIdentifier,
      n.getMessagePrefix,
      HexUtils.valueOf(n.getXPUBVersion),
      n.getAdditionalEIPs.asScala.toList,
      n.getTimestampDelay)
}

case class EthereumTransactionView(
                                    @JsonProperty("block") block: Option[CommonBlockView],
                                    @JsonProperty("hash") hash: String,
                                    @JsonProperty("receiver") receiver: String,
                                    @JsonProperty("sender") sender: String,
                                    @JsonProperty("value") value: String,
                                    @JsonProperty("erc20") erc20: Option[EthereumTransactionView.ERC20],
                                    @JsonProperty("gas_price") gasPrice: String,
                                    @JsonProperty("gas_limit") gasLimit: String,
                                    @JsonProperty("date") date: Date,
                                    @JsonProperty("status") status: Int
                                  ) extends TransactionView

object EthereumTransactionView {
  def apply(tx: EthereumLikeTransaction): EthereumTransactionView = {
    EthereumTransactionView(
      Option(tx.getBlock).map(CommonBlockView.apply),
      tx.getHash,
      tx.getReceiver.toEIP55,
      tx.getSender.toEIP55,
      tx.getValue.toString,
      None,
      tx.getGasPrice.toString,
      tx.getGasLimit.toString,
      tx.getDate,
      tx.getStatus()
    )
  }

  case class ERC20(receiver: String, amount: scala.BigInt)

  object ERC20 {
    def from(erc20Operation: ERC20LikeOperation): ERC20 = {
      ERC20(erc20Operation.getReceiver, erc20Operation.getValue.asScala)
    }
  }

}

case class UnsignedEthereumTransactionView(
                                            @JsonProperty("hash") hash: String,
                                            @JsonProperty("receiver") receiver: String,
                                            @JsonProperty("value") value: String,
                                            @JsonProperty("gas_price") gasPrice: String,
                                            @JsonProperty("gas_limit") gasLimit: String,
                                            @JsonProperty("fees") fees: String,
                                            @JsonProperty("raw_transaction") rawTransaction: String,
                                            @JsonProperty("status") status: Int
                                          ) extends TransactionView

object UnsignedEthereumTransactionView {
  def apply(tx: EthereumLikeTransaction): UnsignedEthereumTransactionView = {
    UnsignedEthereumTransactionView(
      tx.getHash,
      tx.getReceiver.toEIP55,
      tx.getValue.toString,
      tx.getGasPrice.toString,
      tx.getGasLimit.toString,
      (tx.getGasPrice.toBigInt.asScala * tx.getGasLimit.toBigInt.asScala).toString,
      HexUtils.valueOf(tx.serialize()),
      tx.getStatus()
    )
  }
}
