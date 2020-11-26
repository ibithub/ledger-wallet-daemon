package co.ledger.wallet.daemon.database

import java.time.Instant
import java.util.Date

import com.twitter.finagle.postgres.values.ValueDecoder.instance
import com.twitter.finagle.postgres.values.{Buffers, ValueDecoder}
import com.twitter.util.Try

package object core {

  object Decoder {

    import java.text.SimpleDateFormat

    val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")

    def parseDate(dateStr: String): Date = {
      Date.from(Instant.parse(dateStr))
    }

    implicit val date: ValueDecoder[Date] = instance(
      s => Try {
        parseDate(s)
      },
      (b, c) => Try(parseDate(Buffers.readString(b, c)))
    )

  }

  object Ordering {

    sealed trait OperationOrder {
      val value: String
    }

    case object Ascending extends OperationOrder {
      override val value: String = "ASC"
    }

    case object Descending extends OperationOrder {
      override val value: String = "DSC"
    }

  }

  /**
    * Partial Operation is fitting generic operation core model
    */
  case class PartialOperation(uid: String,
                              currencyName: String,
                              currencyFamily: String,
                              date: Date,
                              txHash: String,
                              blockHeight: Option[Long],
                              blockHash: Option[String],
                              blockTime: Option[Date],
                              opType: String,
                              amount: BigInt,
                              fees: BigInt,
                              walletName: String,
                              accountIndex: Int,
                              senders: Seq[String],
                              recipients: Seq[String])

  /**
    * Partial Operation is fitting generic operation core model
    */
  case class PartialEthOperation(uid: String,
                                 currencyName: String,
                                 currencyFamily: String,
                                 date: Date,
                                 txHash: String,
                                 blockHeight: Option[Long],
                                 blockHash: Option[String],
                                 blockTime: Option[Date],
                                 opType: String,
                                 amount: BigInt,
                                 fees: BigInt,
                                 walletName: String,
                                 accountIndex: Int,
                                 gasPrice: String,
                                 gasLimit: String,
                                 gasUsed: String,
                                 status: BigInt,
                                 confirmations: BigInt,
                                 sender: String,
                                 recipient: String)

}
