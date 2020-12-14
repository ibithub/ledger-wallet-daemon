package co.ledger.wallet.daemon.clients

import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

import co.ledger.core.{ErrorCode, WebSocketConnection}
import co.ledger.wallet.daemon.services.LogMsgMaker
import com.github.ghik.silencer.silent
import com.twitter.inject.Logging
import javax.annotation.Nullable
import javax.annotation.concurrent.ThreadSafe
import javax.websocket.{ClientEndpoint, ContainerProvider, OnMessage, Session}

@ThreadSafe
@ClientEndpoint
class ScalaWebSocketClient extends co.ledger.core.WebSocketClient with Logging{
  import ScalaWebSocketClient._

  override def connect(url: String, connection: WebSocketConnection): Unit = {
    if (connection.getConnectionId > 0 && sessions.containsKey(connection.getConnectionId)) {
      val session = sessions.get(connection.getConnectionId)
      warn(LogMsgMaker.newInstance("Connection already exist, ignore request")
        .append("url", url)
        .append("connection", connection.getConnectionId)
        .append("session", session.getId)
        .append("session_open", session.isOpen)
        .toString())
    } else {
      try {
        val session = webSocketContainer.connectToServer(classOf[ScalaWebSocketClient], URI.create(url))
        connection.onConnect(connectionCount.incrementAndGet())
        sessions.put(connection.getConnectionId, session)
        info(LogMsgMaker.newInstance("Connection established")
          .append("url", url)
          .append("session", session.getId)
          .append("connection", connection.getConnectionId)
          .toString())
      } catch {
        case e: Throwable =>
          error(LogMsgMaker.newInstance("Failed to connect to server")
            .append("url", url)
            .toString(), e)
          connection.onError(ErrorCode.HTTP_ERROR, e.getMessage)
      }
    }
  }

  override def send(connection: WebSocketConnection, data: String): Unit = {
    if (!sessions.containsKey(connection.getConnectionId)) {
      warn(LogMsgMaker.newInstance("Fail to send data, no connection")
        .append("connection", connection.getConnectionId)
        .append("data", data)
        .toString())
    } else {
      info(LogMsgMaker.newInstance("Sending data")
        .append("data", data)
        .append("connection", connection.getConnectionId)
        .toString())
      sessions.get(connection.getConnectionId).getBasicRemote.sendText(data)
    }
  }


  override def disconnect(connection: WebSocketConnection): Unit = {
    if (!sessions.containsKey(connection.getConnectionId)) {
      warn(LogMsgMaker.newInstance("Fail to disconnect, not connected")
        .append("connection", connection.getConnectionId)
        .toString())
    } else {
      try {
        @Nullable val session = sessions.remove(connection.getConnectionId)
        if (session != null) session.close()
        info(LogMsgMaker.newInstance("Disconnecting, session closed")
          .append("session", session.getId)
          .append("connection", connection.getConnectionId)
          .toString())
      } catch {
        case e: Throwable =>
          error("Fail to disconnect", e)
          connection.onError(ErrorCode.HTTP_ERROR, e.getMessage)
      } finally {
        connection.onClose()
      }
    }

  }

  // @silent is used here because we have no other way to walk around the
  // ugly Java method without generating warning in Scala. @silent should
  // not be used as a no-brainer to by pass our fatal warning compiler option.
  @silent
  @OnMessage
  // here to avoid error "java.lang.IllegalStateException: Text message handler not found".
  // without this, the account synchronization won't work correctly.
  def processMessage(message: String, session: Session): Unit = Unit

}

object ScalaWebSocketClient {
  private val webSocketContainer = ContainerProvider.getWebSocketContainer
  private val sessions: ConcurrentMap[Int, Session] = new ConcurrentHashMap[Int, Session]()
  private val connectionCount = new AtomicInteger(0)
}
