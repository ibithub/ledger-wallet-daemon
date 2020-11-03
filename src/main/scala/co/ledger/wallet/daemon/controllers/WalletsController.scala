package co.ledger.wallet.daemon.controllers

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.controllers.requests.{CommonMethodValidations, WalletDaemonRequest, WithPoolInfo, WithWalletInfo}
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.filters.DeprecatedRouteFilter
import co.ledger.wallet.daemon.services.WalletsService
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.finatra.validation.{MethodValidation, NotEmpty, ValidationResult}
import javax.inject.Inject

class WalletsController @Inject()(walletsService: WalletsService) extends Controller {

  import WalletsController._

  /**
    * End point queries for wallets views in specified pool.
    *
    */
  get("/pools/:pool_name/wallets") { request: GetWalletsRequest =>
    info(s"GET wallets $request")
    walletsService.wallets(request.offset.getOrElse(DEFAULT_OFFSET),
      request.count.getOrElse(DEFAULT_COUNT), request.poolInfo)
  }

  /**
    * End point queries for wallet view in specified pool by it's name.
    *
    */
  filter[DeprecatedRouteFilter].get("/pools/:pool_name/wallets/:wallet_name") { request: GetWalletRequest =>
    info(s"GET wallet $request")
    walletsService.wallet(request.walletInfo).map {
      case Some(view) => ResponseSerializer.serializeOk(view, request.request, response)
      case None => ResponseSerializer.serializeNotFound(request.request,
        Map("response" -> "Wallet doesn't exist", "wallet_name" -> request.wallet_name), response)
    }
  }

  /**
    * End point to create a instance of wallet within the specified pool.
    *
    */
  post("/pools/:pool_name/wallets") { request: CreateWalletRequest =>
    info(s"CREATE wallet $request")
    walletsService.createWallet(request.currency_name, request.walletInfo, request.is_native_segwit)
  }

}

object WalletsController {
  private val DEFAULT_OFFSET: Int = 0
  private val DEFAULT_COUNT: Int = 20

  case class GetWalletRequest(
                               @RouteParam pool_name: String,
                               @RouteParam wallet_name: String,
                               request: Request
                             ) extends WalletDaemonRequest with WithWalletInfo {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)
  }

  case class GetWalletsRequest(
                                @RouteParam pool_name: String,
                                @QueryParam offset: Option[Int],
                                @QueryParam count: Option[Int],
                                request: Request
                              ) extends WalletDaemonRequest with WithPoolInfo {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateOffset: ValidationResult = ValidationResult.validate(offset.isEmpty || offset.get >= 0, "offset: offset can not be less than zero")

    @MethodValidation
    def validateCount: ValidationResult = ValidationResult.validate(count.isEmpty || count.get > 0, "account_index: index can not be less than 1")
  }

  case class CreateWalletRequest(
                                  @RouteParam pool_name: String,
                                  @NotEmpty @JsonProperty wallet_name: String,
                                  @NotEmpty @JsonProperty currency_name: String,
                                  @JsonProperty is_native_segwit: Boolean = false,
                                  request: Request
                                ) extends WalletDaemonRequest with WithWalletInfo {
    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)

    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)
  }

}
