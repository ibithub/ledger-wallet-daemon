package co.ledger.wallet.daemon.async

import org.slf4j.MDC

import scala.concurrent.ExecutionContext

/**
  * Customized execution context to allow logback to propagate tracing information
  * from parent thread to children threads.
  */
trait MDCPropagatingExecutionContext extends ExecutionContext{
  self =>

  override def prepare(): ExecutionContext = new ExecutionContext {

    val context = Option(MDC.getCopyOfContextMap)

    override def execute(runnable: Runnable): Unit = self.execute(() => {

      val oldContext = Option(MDC.getCopyOfContextMap)

      try {
        context match {
          case Some(c) => MDC.setContextMap(c)
          case None => MDC.clear()
        }
        runnable.run()
      } finally {
        oldContext match {
          case Some(oc) => MDC.setContextMap(oc)
          case None => MDC.clear()
        }
      }
    })

    override def reportFailure(cause: Throwable): Unit = self.reportFailure(cause)
  }
}

object MDCPropagatingExecutionContext {
  object Implicits {
    implicit lazy val global: ExecutionContext = MDCPropagatingExecutionContextWrapper(ExecutionContext.Implicits.global)
  }
}

class MDCPropagatingExecutionContextWrapper(wrapped: ExecutionContext) extends ExecutionContext with MDCPropagatingExecutionContext {
  override def execute(runnable: Runnable): Unit = wrapped.execute(runnable)

  override def reportFailure(cause: Throwable): Unit = wrapped.reportFailure(cause)
}

object MDCPropagatingExecutionContextWrapper {
  def apply(wrapped: ExecutionContext): MDCPropagatingExecutionContextWrapper = {
    new MDCPropagatingExecutionContextWrapper(wrapped)
  }
}
