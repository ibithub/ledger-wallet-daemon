package co.ledger.wallet.daemon.utils

import java.time.temporal.{ChronoUnit, TemporalAdjusters}
import java.time.{DayOfWeek, LocalDate, ZoneId}
import java.util.Date

import co.ledger.core
import co.ledger.core.TimePeriod

import scala.collection.mutable

object Utils {

  import com.twitter.util.{Return, Throw, Future => TwitterFuture, Promise => TwitterPromise}

  import scala.collection.JavaConverters._
  import scala.concurrent.{ExecutionContext, Future => ScalaFuture, Promise => ScalaPromise}
  import scala.util.{Failure, Success}

  /** Convert from a Twitter Future to a Scala Future */
  implicit class RichTwitterFuture[A](val tf: TwitterFuture[A]) extends AnyVal {
    def asScala(): ScalaFuture[A] = {
      val promise: ScalaPromise[A] = ScalaPromise()
      tf.respond {
        case Return(value) => promise.success(value)
        case Throw(exception) => promise.failure(exception)
      }
      promise.future
    }
  }

  /** Convert from a Scala Future to a Twitter Future */
  implicit class RichScalaFuture[A](val sf: ScalaFuture[A]) extends AnyVal {
    def asTwitter()(implicit e: ExecutionContext): TwitterFuture[A] = {
      val promise: TwitterPromise[A] = new TwitterPromise[A]()
      sf.onComplete {
        case Success(value) => promise.setValue(value)
        case Failure(exception) => promise.setException(exception)
      }
      promise
    }
  }

  implicit class AsArrayList[T](val input: Seq[T]) extends AnyVal {
    def asArrayList: java.util.ArrayList[T] = new java.util.ArrayList[T](input.asJava)
  }

  def newConcurrentSet[T]: mutable.Set[T] = {
    java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap[T, java.lang.Boolean]()).asScala
  }

  implicit class RichBigInt(val i: core.BigInt) extends AnyVal {
    def asScala: BigInt = BigInt(i.toString(10))
  }

  implicit class RichCoreBigInt(val i: BigInt) extends AnyVal {
    def asCoreBigInt: core.BigInt = core.BigInt.fromIntegerString(i.toString(), 10)
  }

  /**
    * Calculate how many values are expected regarding date interval and period
    * Note that our API is proposing inclusive bound ranges whereas java is proposing exclusive end bounds
    *
    * @return How many partial or complete DAYs / WEEKs / MONTHs are involved inside the inclusive [start ; end] interval
    */
  def intervalSize(startInclusive: LocalDate, endInclusive: LocalDate, period: core.TimePeriod): Int = {
    period match {
      case TimePeriod.HOUR => ChronoUnit.HOURS.between(startInclusive, endInclusive.plus(1, ChronoUnit.HOURS)).intValue()
      case TimePeriod.DAY => ChronoUnit.DAYS.between(startInclusive, endInclusive.plusDays(1)).intValue()
      case TimePeriod.WEEK =>
        ChronoUnit.WEEKS.between(
          startInclusive `with` TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY),
          (endInclusive `with` TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusDays(1)).intValue()
      case TimePeriod.MONTH =>
        ChronoUnit.MONTHS.between(
          startInclusive `with` TemporalAdjusters.firstDayOfMonth(),
          endInclusive `with` TemporalAdjusters.firstDayOfNextMonth()).intValue()
    }
  }

  def preview(s: String, n: Int): String = {
    if (s.length <= n) {
      s
    } else {
      s.substring(0, n).concat("[TRUNCATED]")
    }
  }

  implicit def dateToLocalDate(date: Date): LocalDate = date.toInstant.atZone(ZoneId.systemDefault()).toLocalDate
}
