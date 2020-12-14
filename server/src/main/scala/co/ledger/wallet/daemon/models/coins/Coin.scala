package co.ledger.wallet.daemon.models.coins

import java.util.Date

import co.ledger.core.EthereumLikeBlock
import co.ledger.wallet.daemon.models.coins.Coin.BlockView
import com.fasterxml.jackson.annotation.JsonProperty


object Coin {

  trait NetworkParamsView

  trait TransactionView

  trait BlockView

  trait InputView

  trait OutputView

}

case class CommonBlockView(
                            @JsonProperty("hash") hash: String,
                            @JsonProperty("height") height: Long,
                            @JsonProperty("time") time: Date
                          ) extends BlockView

object CommonBlockView {
  def apply(b: EthereumLikeBlock): CommonBlockView = {
    CommonBlockView(b.getHash, b.getHeight, b.getTime)
  }
}
