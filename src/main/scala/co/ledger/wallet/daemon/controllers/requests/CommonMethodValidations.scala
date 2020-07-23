package co.ledger.wallet.daemon.controllers.requests

import java.text.{ParseException, SimpleDateFormat}
import java.util.Locale

import co.ledger.core.TimePeriod
import co.ledger.wallet.daemon.models.FeeMethod
import com.twitter.finatra.validation.ValidationResult

object CommonMethodValidations {

  def validateOptionalAccountIndex(accountIndex: Option[Int]): ValidationResult =
    ValidationResult.validate(accountIndex.isEmpty || accountIndex.get >= 0, "account_index: index can not be less than zero")

  def validateName(name: String, nameStr: String): ValidationResult = {
    ValidationResult.validate(
      REGEX.pattern.matcher(nameStr).matches,
      s"$name: invalid $name, $name should match ${REGEX.toString()}")
  }

  def validateFees(feeAmount: Option[BigInt], feeLevel: Option[String]): ValidationResult = {
    ValidationResult.validate(feeAmount.isDefined
      || (feeLevel.isDefined && FeeMethod.isValid(feeLevel.get)),
      "fee_amount or fee_level must be defined, fees_level must be one of 'FAST', 'NORMAL', 'SLOW'")
  }

  def validateTimePeriod(timeInterval: String): ValidationResult = {
    try {
      TimePeriod.valueOf(timeInterval)
      ValidationResult.Valid()
    } catch {
      case _: IllegalArgumentException =>
        ValidationResult.validate(false, s"Time interval must be one of ${TimePeriod.values().toList}")
    }
  }

  def validateDates(start: String, end: String): ValidationResult = {
    try {
      val startDate = DATE_FORMATTER.parse(start)
      val endDate = DATE_FORMATTER.parse(end)
      ValidationResult.validate(startDate.before(endDate), s"Start time '$start' must be earlier than end time '$end'")
    } catch {
      case _: ParseException => ValidationResult.validate(false, "Invalid time format, it must be 'yyyy-MM-dd'T'HH:mm:ss'Z''")
    }
  }

  val DATE_FORMATTER: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)

  private val REGEX = "([0-9a-zA-Z]+[_]?)+[0-9a-zA-Z]+".r
}
