package co.ledger.wallet.daemon.filters

import co.ledger.wallet.daemon.models.{AccountDerivationView, AccountExtendedDerivationView}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finatra.http.exceptions.BadRequestException
import com.twitter.finatra.http.marshalling.MessageBodyManager
import com.twitter.util.Future
import javax.inject.Inject

class AccountCreationFilter @Inject()(messageBodyManager: MessageBodyManager) extends SimpleFilter[Request, Response] {

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val accountCreationBody = messageBodyManager.read[AccountDerivationView](request)
    accountCreationBody.derivations.foreach { derivation =>
      if (derivation.pubKey.isEmpty) {
        throw new BadRequestException("derivations.pub_key: field is required")
      } else if (derivation.chainCode.isEmpty) { throw new BadRequestException("derivations.chain_code: field is required") }
    }
    AccountCreationContext.setAccountCreationBody(request, accountCreationBody)
    service(request)
  }
}

class AccountExtendedCreationFilter @Inject()(messageBodyManager: MessageBodyManager) extends SimpleFilter[Request, Response] {

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val accountCreationBody = messageBodyManager.read[AccountExtendedDerivationView](request)
    accountCreationBody.derivations.foreach { derivation =>
      if (derivation.extKey.isEmpty) {
        throw new BadRequestException("derivations.extended_key: field is required")
      }
    }
    ExtendedAccountCreationContext.setAccountExtendedCreationBody(request, accountCreationBody)
    service(request)
  }
}

object AccountCreationContext {
  private val AccountCreationField = Request.Schema.newField[AccountDerivationView]()

  implicit class AccountCreationContextSyntax(val request: Request) extends AnyVal {
    def accountCreationBody: AccountDerivationView = request.ctx(AccountCreationField)
  }

  def setAccountCreationBody(request: Request, accountCreationBody: AccountDerivationView): Unit = {
    request.ctx.update(AccountCreationField, accountCreationBody)
  }
}

object ExtendedAccountCreationContext {
  private val ExtendedAccountCreationField = Request.Schema.newField[AccountExtendedDerivationView]()

  implicit class AccountExtendedCreationContextSyntax(val request: Request) extends AnyVal {
    def accountExtendedCreationBody: AccountExtendedDerivationView = request.ctx(ExtendedAccountCreationField)
  }

  def setAccountExtendedCreationBody(request: Request, accountCreationBody: AccountExtendedDerivationView): Unit = {
    request.ctx.update(ExtendedAccountCreationField, accountCreationBody)
  }
}
