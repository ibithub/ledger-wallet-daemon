package co.ledger.wallet.daemon.utils

import java.time.LocalDate

import co.ledger.core.TimePeriod
import co.ledger.wallet.daemon.controllers.requests.CommonMethodValidations.DATE_FORMATTER
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

@Test
class UtilsTest extends AssertionsForJUnit {

  @Test
  def testDaysPeriod(): Unit = {
    // Same day expect 1
    val d1: LocalDate = Utils.dateToLocalDate(DATE_FORMATTER.parse("2020-01-01T00:00:00Z"))
    assert(Utils.intervalSize(d1, d1, TimePeriod.DAY) == 1)

    // The day after expect 2
    val d2: LocalDate = Utils.dateToLocalDate(DATE_FORMATTER.parse("2020-01-02T00:00:00Z"))
    assert(Utils.intervalSize(d1, d2, TimePeriod.DAY) == 2)


    // The day 1y later (The year 2020 has 366 days), as this is intended to be inclusive we expect 367
    val d3: LocalDate = Utils.dateToLocalDate(DATE_FORMATTER.parse("2021-01-01T16:00:00Z"))
    assert(Utils.intervalSize(d1, d3, TimePeriod.DAY) == 367)
  }

  @Test
  def testWeeksPeriod(): Unit = {
    // Same day expect 1
    val d1: LocalDate = Utils.dateToLocalDate(DATE_FORMATTER.parse("2020-01-01T14:14:00Z"))
    assert(Utils.intervalSize(d1, d1, TimePeriod.WEEK) == 1)

    // The next sunday expect 1 as this is during the same week
    val d2: LocalDate = Utils.dateToLocalDate(DATE_FORMATTER.parse("2020-01-05T23:59:59Z"))
    assert(Utils.intervalSize(d1, d2, TimePeriod.WEEK) == 1)

    // The next monday expect 2
    val d3: LocalDate = Utils.dateToLocalDate(DATE_FORMATTER.parse("2020-01-06T23:59:59Z"))
    assert(Utils.intervalSize(d1, d3, TimePeriod.WEEK) == 2)

    // The 31th December expect 53 weeks (as there is approximately 52,1 weeks in a year)
    val d4: LocalDate = Utils.dateToLocalDate(DATE_FORMATTER.parse("2020-12-31T00:00:00Z"))
    assert(Utils.intervalSize(d1, d4, TimePeriod.WEEK) == 53)
  }

  @Test
  def testMonthsPeriod(): Unit = {
    // Same day expect 1
    val d1: LocalDate = Utils.dateToLocalDate(DATE_FORMATTER.parse("2020-01-01T12:00:00Z"))
    assert(Utils.intervalSize(d1, d1, TimePeriod.MONTH) == 1)

    // The day after expect 1, same month
    val d2: LocalDate = Utils.dateToLocalDate(DATE_FORMATTER.parse("2020-01-02T01:00:00Z"))
    assert(Utils.intervalSize(d1, d2, TimePeriod.MONTH) == 1)

    // The month after, expect 2 (January and February)
    val d3: LocalDate = Utils.dateToLocalDate(DATE_FORMATTER.parse("2020-02-01T23:59:59Z"))
    assert(Utils.intervalSize(d1, d3, TimePeriod.MONTH) == 2)

    // The 1 year exactly later, expect 13 months
    val d4: LocalDate = Utils.dateToLocalDate(DATE_FORMATTER.parse("2021-01-01T00:00:00Z"))
    assert(Utils.intervalSize(d1, d4, TimePeriod.MONTH) == 13)
  }
}
