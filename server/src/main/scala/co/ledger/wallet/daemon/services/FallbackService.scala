package co.ledger.wallet.daemon.services

import java.net.URL

import cats.data.OptionT
import cats.instances.future._
import co.ledger.wallet.daemon.clients.ScalaHttpClientPool
import co.ledger.wallet.daemon.utils.NetUtils
import co.ledger.wallet.daemon.utils.Utils._
import com.twitter.finagle.http.{Method, Request, Response}
import org.web3j.abi.datatypes.{Address, Function, Type, Uint}
import org.web3j.abi.{FunctionEncoder, TypeReference}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object FallbackService extends DaemonService {

  def encodeBalanceFunction(address: String): Try[String] = {
    info(s"Try to encode balance function with address: $address")
    val function = new Function(
      "balanceOf",
      List[Type[_]](new Address(address.replaceFirst("0x", ""))).asJava,
      List[TypeReference[_ <: Type[_]]](new TypeReference[Uint]() {}).asJava
    )
    Try(FunctionEncoder.encode(function))
  }

  def getBalance(contract: Option[String], address: String, service: ScalaHttpClientPool, url: URL)(implicit ec: ExecutionContext): OptionT[Future, BigInt] =
    for {
      body <- OptionT.liftF(Future.fromTry(contract match {
        case Some(contractAddress) =>
          encodeBalanceFunction(address).map { data =>
            s"""{"jsonrpc":"2.0","method":"eth_call","params":[{"to": "$contractAddress", "data": "$data"}, "latest"],"id":1}"""
          }
        case None => Success(s"""{"jsonrpc":"2.0","method":"eth_getBalance","params":["$address", "latest"],"id":1}""")
      }))
      response <- {
        val request = Request(Method.Post, url.getPath).host(url.getHost)
        request.setContentString(body)
        request.setContentType("application/json")
        val fut: Future[Response] = service.execute(NetUtils.urlToHost(url), request).asScala()
        fut.onComplete {
          case Success(v) => info(s"Successfully retrieve json response from provider : $v")
          case Failure(t) => error("Unable to fetch from fallback provider", t)
        }
        OptionT.liftF(fut)
      }
      result <- OptionT.liftF(Future.fromTry(Try {
        import io.circe.parser.parse
        val json = parse(response.contentString)
        val balance = json.flatMap { j =>
          j.hcursor.get[String]("result")
        }.map(_.replaceFirst("0x", "")).map {
          case "" => BigInt(0)
          case res => BigInt(res, 16)
        }
        balance.getOrElse(throw new Exception(s"Failed to parse fallback provider result from response : ${response.contentString}"))
      }))
    } yield result

}
