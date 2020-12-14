package co.ledger.wallet.daemon.filters

import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import com.twitter.finagle.http.Request

object AuthentifiedUserContext {
  private val UserField = Request.Schema.newField[AuthentifiedUser]()

  implicit class UserContextSyntax(val request: Request) extends AnyVal {
    def user: AuthentifiedUser = request.ctx(UserField)
  }
  def setUser(request: Request, user: User): Unit = request.ctx.update[AuthentifiedUser](UserField, AuthentifiedUser(user))
}
case class AuthentifiedUser(get: User)
