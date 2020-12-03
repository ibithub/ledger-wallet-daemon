package co.ledger.wallet.daemon.controllers

import java.time.{Instant, Period, ZoneId}
import java.util.{Date, UUID}

import co.ledger.core.TimePeriod
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.controllers.requests.CommonMethodValidations.DATE_FORMATTER
import co.ledger.wallet.daemon.controllers.requests._
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.filters.AccountCreationContext._
import co.ledger.wallet.daemon.filters.ExtendedAccountCreationContext._
import co.ledger.wallet.daemon.filters.{AccountCreationFilter, AccountExtendedCreationFilter, DeprecatedRouteFilter}
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.{TokenAccountInfo, UTXOView}
import co.ledger.wallet.daemon.services.{AccountsService, OperationQueryParams}
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.finatra.validation.{MethodValidation, ValidationResult}
import javax.inject.Inject

import scala.concurrent.Future

class AccountsController @Inject()(accountsService: AccountsService) extends Controller {

  import AccountsController._

  prefix("/pools/:pool_name/wallets/:wallet_name") {

    // End point queries for account views with specified pool name and wallet name.
    get("/accounts") { request: AccountsRequest =>
      info(s"GET accounts $request")
      accountsService.accounts(request.walletInfo)
    }

    // End point queries for derivation information view of next account creation.
    filter[DeprecatedRouteFilter].get("/accounts/next") { request: AccountCreationInfoRequest =>
      info(s"GET account creation info $request")
      accountsService.nextAccountCreationInfo(request.account_index, request.walletInfo)
    }

    // End point to create a new account within the specified pool and wallet.
    filter[DeprecatedRouteFilter].filter[AccountCreationFilter]
      .post("/accounts") { request: AccountsRequest =>
        info(s"CREATE account $request, " +
          s"Parameters(pool_name: ${request.pool_name}, wallet_name: ${request.wallet_name}), " +
          s"Body(${request.request.accountCreationBody}")
        accountsService.createAccount(request.request.accountCreationBody, request.walletInfo)
      }

    // End point to create a new account within the specified pool and wallet with extended keys info.
    filter[DeprecatedRouteFilter].filter[AccountExtendedCreationFilter]
      .post("/accounts/extended") { request: AccountsRequest =>
        info(s"CREATE account ${request.request}, " +
          s"Parameters(pool_name: ${request.pool_name}, wallet_name: ${request.wallet_name}), " +
          s"Body(${request.request.accountExtendedCreationBody}")
        accountsService.createAccountWithExtendedInfo(request.request.accountExtendedCreationBody, request.walletInfo)
      }

    // End point queries for derivation information view of next account creation (with extended key).
    filter[DeprecatedRouteFilter].get("/accounts/next_extended") { request: AccountCreationInfoRequest =>
      info(s"GET account creation info $request")
      accountsService.nextExtendedAccountCreationInfo(request.account_index, request.walletInfo)
    }

    filter[DeprecatedRouteFilter].get("/accounts/:account_index") { request: AccountRequest =>
      info(s"GET account $request")
      accountsService.account(request.accountInfo).map {
        case Some(view) => ResponseSerializer.serializeOk(view, request.request, response)
        case None => ResponseSerializer.serializeNotFound(request.request, Map("response" -> "Account doesn't exist", "account_index" -> request.account_index), response)
      }.recover {
        case _: WalletPoolNotFoundException => ResponseSerializer.serializeBadRequest(request.request,
          Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
          response)
        case _: WalletNotFoundException => ResponseSerializer.serializeBadRequest(request.request,
          Map("response" -> "Wallet doesn't exist", "wallet_name" -> request.wallet_name),
          response)
        case e: Throwable => ResponseSerializer.serializeInternalError(request.request, response, e)
      }
    }

    // End point queries for account views with specified pool name and wallet name.
    post("/accounts/:account_index/resync") { request: AccountRequest =>
      info(s"Resync accounts $request")
      accountsService.resynchronizeAccount(request.accountInfo)
    }

    get("/accounts/:account_index/sync-status") { request: AccountRequest =>
      info(s"get account sync status $request")
      accountsService.syncStatus(request.accountInfo)
    }

    // End point queries for account view with specified pool, wallet name, and unique account index.
    prefix("/accounts/:account_index") {

      // End point queries for fresh addresses with specified pool, wallet name and unique account index.
      get("/addresses/fresh") { request: AccountRequest =>
        info(s"GET fresh addresses $request")
        accountsService.accountFreshAddresses(request.accountInfo)
      }

      // End point queries for addresses by range with specified pool, wallet name and unique account index.
      get("/addresses") { request: AccountAddressRequest =>
        info(s"GET addresses in range $request")
        accountsService.accountAddressesInRange(request.from, request.to, request.accountInfo)
      }

      // End point queries for derivation path with specified pool, wallet name and unique account index.
      filter[DeprecatedRouteFilter].get("/path") { request: AccountRequest =>
        info(s"GET account derivation path $request")
        accountsService.accountDerivationPath(request.accountInfo)
      }

      // End point queries for operation views with specified pool, wallet name, and unique account index.
      get("/operations") { request: OperationsRequest =>
        info(s"GET account operations $request")
        request.contract match {
          case Some(contract) =>
            accountsService.getBatchedERC20Operations(TokenAccountInfo(contract, request.accountInfo), request.offset, request.batch)
          case None =>
            accountsService.accountOperations(OperationQueryParams(request.previous, request.next, request.batch, request.full_op), request.accountInfo)
        }
      }
      // End point queries for operation views with specified pool, wallet name, and unique account index.
      get("/operations/latests") { request: LatestOperationsRequest =>
        info(s"GET latests account operations $request")
        accountsService.latestOperations(request.accountInfo, request.length)
      }
      // End point queries for account balance
      get("/balance") { request: BalanceRequest =>
        info(s"GET account balance $request")
        accountsService.getBalance(request.contract, request.accountInfo)
      }

      // End point queries for account xpub
      get("/xpub") { request: AccountRequest =>
        info(s"GET account xpub $request")
        accountsService.getXpub(request.accountInfo)
      }

      // End point queries for operation view with specified uid, return the first operation of this account if uid is 'first'.
      filter[DeprecatedRouteFilter].get("/operations/:uid") { request: OperationRequest =>
        info(s"GET account operation $request")
        request.uid match {
          case "first" => accountsService.firstOperation(request.accountInfo)
            .map {
              case Some(view) => ResponseSerializer.serializeOk(view, request.request, response)
              case None => ResponseSerializer.serializeNotFound(request.request, Map("response" -> "Account is empty"), response)
            }
          case _ => accountsService.accountOperation(request.uid, request.full_op, request.accountInfo)
            .map {
              case Some(view) => ResponseSerializer.serializeOk(view, request.request, response)
              case None => ResponseSerializer.serializeNotFound(request.request, Map("response" -> "Account operation doesn't exist", "uid" -> request.uid), response)
            }
        }
      }

      // Return the balances and operation counts history in the order of the starting time to the end time.
      get("/history") { request: HistoryRequest =>
        info(s"Get history $request")
        for {
          accountOpt <- accountsService.getAccount(request.accountInfo)
          account <- accountOpt.map(Future.successful).getOrElse(Future.failed(AccountNotFoundException(request.account_index)))
          balances <- account.balances(request.start, request.end, request.timePeriod)
        } yield HistoryResponse(balances)
      }

      // Synchronize a single account
      post("/operations/synchronize") { request: AccountRequest =>
        accountsService.synchronizeAccount(request.accountInfo)
      }

      post("/operations/repush") { request: RepushRequest =>
        accountsService.repushOperations(request.accountInfo, request.from)
      }

      // List of utxos available on this account
      get("/utxo") { request: UtxoAccountRequest =>
        accountsService.getUtxo(request.accountInfo, request.offset, request.batch)
          .map(utxoResponse => UtxoAccountResponse(utxoResponse._1, utxoResponse._2))
      }

      // List of tokens on this account
      get("/tokens") { request: AccountRequest =>
        accountsService.getTokenAccounts(request.accountInfo)
      }

      // operations of all tokens on this account
      filter[DeprecatedRouteFilter].get("/tokens/operations") { request: AccountRequest =>
        accountsService.getERC20Operations(request.accountInfo)
      }

      // given token address, get the token on this account
      filter[DeprecatedRouteFilter].get("/tokens/:token_address") { request: TokenAccountRequest =>
        accountsService.getTokenAccount(request.tokenAccountInfo)
      }

      // Return the balances and operation counts history of token accounts in the order of the starting time to the end time.
      get("/tokens/:token_address/history") { request: TokenHistoryRequest =>
        for {
          balances <- accountsService.getTokenCoreAccountBalanceHistory(request.tokenAccountInfo, request.startDate, request.endDate, request.timePeriod)
        } yield TokenHistoryResponse(balances)
      }

      // given token address, get the operations on this token
      get("/tokens/:token_address/operations") { request: TokenAccountRequest =>
        accountsService.getBatchedERC20Operations(request.tokenAccountInfo, request.offset, request.batch)
      }
    }
  }

}

object AccountsController {
  private val DEFAULT_BATCH: Int = 20
  private val DEFAULT_LATEST: Int = 5
  private val DEFAULT_OFFSET: Int = 0
  private val DEFAULT_OPERATION_MODE: Int = 0


  abstract class BaseAccountRequest extends WalletDaemonRequest with WithWalletInfo {
    val pool_name: String
    val wallet_name: String

    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)
  }

  abstract class BaseSingleAccountRequest extends BaseAccountRequest with WithAccountInfo {
    val account_index: Int

    @MethodValidation
    def validateAccountIndex: ValidationResult = ValidationResult.validate(account_index >= 0, "account_index: index can not be less than zero")
  }

  case class HistoryResponse(balances: List[BigInt])

  case class TokenHistoryResponse(balances: List[BigInt])

  case class HistoryRequest(
                             @RouteParam override val pool_name: String,
                             @RouteParam override val wallet_name: String,
                             @RouteParam override val account_index: Int,
                             @QueryParam start: String, @QueryParam end: String, @QueryParam timeInterval: String,
                             request: Request
                           ) extends BaseSingleAccountRequest {

    def timePeriod: TimePeriod = TimePeriod.valueOf(timeInterval)

    def startDate: Date = DATE_FORMATTER.parse(start)

    def endDate: Date = DATE_FORMATTER.parse(end)

    @MethodValidation
    def validateDate: ValidationResult = {
      val validateDateFormat = CommonMethodValidations.validateDates(start, end)
      if (validateDateFormat.isValid) {
        val validateTP = validateTimePeriod
        if (validateTP.isValid) {
          validatePeriodSize
        } else {
          validateTP
        }
      } else {
        validateDateFormat
      }
    }

    def validatePeriodSize: ValidationResult = {
      val startLocalDate = Instant.ofEpochMilli(startDate.getTime()).atZone(ZoneId.systemDefault()).toLocalDate()
      val endLocalDate = Instant.ofEpochMilli(endDate.getTime()).atZone(ZoneId.systemDefault()).toLocalDate()
      val period = Period.between(startLocalDate, endLocalDate)
      timePeriod match {
        case TimePeriod.HOUR =>
          ValidationResult.validate(
            period.minusMonths(1).isNegative(),
            "can't return hourly history for period more than one month"
          )
        case TimePeriod.DAY =>
          ValidationResult.validate(
            period.minusYears(3).isNegative(),
            "can't return daily history for period more than three years"
          )
        case TimePeriod.WEEK =>
          ValidationResult.validate(
            period.minusYears(10).isNegative(),
            "can't return weekly history for period more than ten years"
          )
        case TimePeriod.MONTH =>
          ValidationResult.validate(
            period.minusYears(100).isNegative(),
            "can't return monthly history for period more than one hundred years"
          )
      }
    }

    def validateTimePeriod: ValidationResult = CommonMethodValidations.validateTimePeriod(timeInterval)
  }

  case class TokenHistoryRequest(
                                  @RouteParam override val pool_name: String,
                                  @RouteParam override val wallet_name: String,
                                  @RouteParam override val account_index: Int,
                                  @RouteParam override val token_address: String,
                                  @QueryParam start: String, @QueryParam end: String, @QueryParam timeInterval: String,
                                  request: Request
                                ) extends BaseSingleAccountRequest with WithTokenAccountInfo {
    def timePeriod: TimePeriod = TimePeriod.valueOf(timeInterval)

    def startDate: Date = DATE_FORMATTER.parse(start)

    def endDate: Date = DATE_FORMATTER.parse(end)

    @MethodValidation
    def validateDate: ValidationResult = CommonMethodValidations.validateDates(start, end)

    @MethodValidation
    def validateTimePeriod: ValidationResult = CommonMethodValidations.validateTimePeriod(timeInterval)
  }

  case class AccountRequest(
                             @RouteParam override val pool_name: String,
                             @RouteParam override val wallet_name: String,
                             @RouteParam override val account_index: Int,
                             request: Request) extends BaseSingleAccountRequest

  case class AccountAddressRequest(
                                    @RouteParam override val pool_name: String,
                                    @RouteParam override val wallet_name: String,
                                    @RouteParam override val account_index: Int,
                                    @QueryParam from: Long = 0,
                                    @QueryParam to: Long = 1,
                                    request: Request) extends BaseSingleAccountRequest

  case class AccountsRequest(
                              @RouteParam override val pool_name: String,
                              @RouteParam override val wallet_name: String,
                              request: Request
                            ) extends BaseAccountRequest

  case class TokenAccountRequest(
                                  @RouteParam pool_name: String,
                                  @RouteParam wallet_name: String,
                                  @RouteParam account_index: Int,
                                  @RouteParam token_address: String,
                                  @QueryParam offset: Int = DEFAULT_OFFSET,
                                  @QueryParam batch: Int = DEFAULT_BATCH,
                                  request: Request
                                )
    extends BaseSingleAccountRequest with WithTokenAccountInfo

  case class UtxoAccountResponse(utxos: List[UTXOView], count: Int)

  case class UtxoAccountRequest(
                                 @RouteParam pool_name: String,
                                 @RouteParam wallet_name: String,
                                 @RouteParam account_index: Int,
                                 @QueryParam offset: Int = 0,
                                 @QueryParam batch: Int = Int.MaxValue,
                                 request: Request
                               )
    extends BaseSingleAccountRequest

  case class AccountCreationInfoRequest(
                                         @RouteParam pool_name: String,
                                         @RouteParam wallet_name: String,
                                         @QueryParam account_index: Option[Int],
                                         request: Request
                                       ) extends WalletDaemonRequest with WithWalletInfo {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)

    @MethodValidation
    def validateAccountIndex: ValidationResult = CommonMethodValidations.validateOptionalAccountIndex(account_index)
  }

  case class OperationsRequest(
                                @RouteParam override val pool_name: String,
                                @RouteParam override val wallet_name: String,
                                @RouteParam override val account_index: Int,
                                @QueryParam next: Option[UUID],
                                @QueryParam previous: Option[UUID],
                                @QueryParam offset: Int = DEFAULT_OFFSET,
                                @QueryParam batch: Int = DEFAULT_BATCH,
                                @QueryParam full_op: Int = DEFAULT_OPERATION_MODE,
                                @QueryParam contract: Option[String],
                                request: Request
                              ) extends BaseSingleAccountRequest {

    @MethodValidation
    def validateBatch: ValidationResult = ValidationResult.validate(batch > 0, "batch: batch should be greater than zero")

  }

  case class LatestOperationsRequest(
                                      @RouteParam override val pool_name: String,
                                      @RouteParam override val wallet_name: String,
                                      @RouteParam override val account_index: Int,
                                      @QueryParam length: Int = DEFAULT_LATEST,
                                      @QueryParam contract: Option[String],
                                      request: Request
                                    ) extends BaseSingleAccountRequest {

    @MethodValidation
    def validateLatest: ValidationResult = ValidationResult.validate(length > 0, "latest: length should be greater than zero")

  }

  case class BalanceRequest(
                             @RouteParam override val pool_name: String,
                             @RouteParam override val wallet_name: String,
                             @RouteParam override val account_index: Int,
                             // TODO find better way to handle ERC20 contract
                             @QueryParam contract: Option[String],
                             request: Request
                           ) extends BaseSingleAccountRequest


  case class OperationRequest(
                               @RouteParam override val pool_name: String,
                               @RouteParam override val wallet_name: String,
                               @RouteParam override val account_index: Int,
                               @RouteParam uid: String,
                               @QueryParam full_op: Int = 0,
                               request: Request
                             ) extends BaseSingleAccountRequest

  case class RepushRequest(
                             @RouteParam override val pool_name: String,
                             @RouteParam override val wallet_name: String,
                             @RouteParam override val account_index: Int,
                             @QueryParam from: Option[Long],
                             request: Request
                           ) extends BaseSingleAccountRequest
}
