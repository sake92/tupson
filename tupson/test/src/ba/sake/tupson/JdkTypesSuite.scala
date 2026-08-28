package ba.sake.tupson

import java.math.*
import java.time.*
import java.time.format.DateTimeParseException
import java.util.*

class JdkTypesSuite extends munit.FunSuite {

  private def roundtripTest[T](name: String, value: T, json: String)(using rw: JsonRW[T]) =
    test(s"$name roundtrip") {
      assertEquals(value.toJson(spaces = 0), json)
      assertEquals(json.parseJson[T], value)
    }

  roundtripTest("LocalDate", LocalDate.of(2026, 8, 28), "\"2026-08-28\"")
  roundtripTest("LocalTime", LocalTime.of(12, 34, 56), "\"12:34:56\"")
  roundtripTest(
    "LocalDateTime",
    LocalDateTime.of(2026, 8, 28, 12, 34, 56),
    "\"2026-08-28T12:34:56\""
  )
  roundtripTest(
    "OffsetDateTime",
    OffsetDateTime.of(2026, 8, 28, 12, 34, 56, 0, ZoneOffset.ofHours(2)),
    "\"2026-08-28T12:34:56+02:00\""
  )
  roundtripTest(
    "OffsetTime",
    OffsetTime.of(12, 34, 56, 0, ZoneOffset.ofHours(2)),
    "\"12:34:56+02:00\""
  )
  roundtripTest(
    "ZonedDateTime",
    ZonedDateTime.of(2026, 8, 28, 12, 34, 56, 0, ZoneOffset.UTC),
    "\"2026-08-28T12:34:56Z\""
  )
  roundtripTest("ZoneId region", ZoneId.of("Europe/Berlin"), "\"Europe/Berlin\"")
  roundtripTest("ZoneId offset", ZoneOffset.ofHours(2), "\"+02:00\"")
  roundtripTest("YearMonth", YearMonth.of(2026, 8), "\"2026-08\"")
  roundtripTest("MonthDay", MonthDay.of(8, 28), "\"--08-28\"")
  roundtripTest("Year", Year.of(2026), "\"2026\"")
  roundtripTest("Month", Month.AUGUST, "\"AUGUST\"")
  roundtripTest("DayOfWeek", DayOfWeek.SATURDAY, "\"SATURDAY\"")

  test("LocalDate type mismatch") {
    val ex = intercept[ParsingException] {
      "123".parseJson[LocalDate]
    }
    assertEquals(ex.errors, Seq(ParseError("$", "should be LocalDate but it is Number", Some("123"))))
  }

  test("LocalDate malformed string propagates DateTimeParseException") {
    intercept[DateTimeParseException] {
      "\"bddsfsdf\"".parseJson[LocalDate]
    }
  }

  test("Month unknown name propagates IllegalArgumentException") {
    intercept[IllegalArgumentException] {
      "\"SMARCH\"".parseJson[Month]
    }
  }

  test("BigDecimal roundtrip exact") {
    val value = BigDecimal("1234567890.12345678901234567890")
    assertEquals(value.toJson(spaces = 0), "1234567890.12345678901234567890")
    assertEquals("1234567890.12345678901234567890".parseJson[BigDecimal], value)
  }

  test("BigDecimal parses exponent form") {
    assertEquals("1e3".parseJson[BigDecimal], BigDecimal("1e3"))
  }

  test("BigDecimal type mismatch") {
    val ex = intercept[ParsingException] {
      "\"abc\"".parseJson[BigDecimal]
    }
    assertEquals(ex.errors, Seq(ParseError("$", "should be BigDecimal but it is String", Some("\"abc\""))))
  }

  test("BigInteger roundtrip exact") {
    val value = new BigInteger("123456789012345678901234567890")
    assertEquals(value.toJson(spaces = 0), "123456789012345678901234567890")
    assertEquals("123456789012345678901234567890".parseJson[BigInteger], value)
  }

  test("BigInteger rejects fractional JSON numbers with NumberFormatException") {
    intercept[NumberFormatException] {
      "1.5".parseJson[BigInteger]
    }
  }

  test("Currency roundtrip") {
    assertEquals(Currency.getInstance("EUR").toJson(spaces = 0), "\"EUR\"")
    assertEquals("\"EUR\"".parseJson[Currency], Currency.getInstance("EUR"))
  }

  test("Currency unknown code propagates IllegalArgumentException") {
    intercept[IllegalArgumentException] {
      "\"XXXZZZ\"".parseJson[Currency]
    }
  }

  test("Locale roundtrip") {
    assertEquals(Locale.US.toJson(spaces = 0), "\"en-US\"")
    assertEquals("\"en-US\"".parseJson[Locale], Locale.US)
    assertEquals("\"sr-BA\"".parseJson[Locale], Locale.forLanguageTag("sr-BA"))
  }
}
