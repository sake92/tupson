package ba.sake.tupson

import java.time.{Duration, Instant, LocalDate, LocalDateTime, LocalTime, Period}
import java.time.{OffsetDateTime, OffsetTime, ZoneId, ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeParseException
import JsonRW2.{parseJson as parseJson2, toJson as toJson2}

class JsonRW2TimeSuite extends munit.FunSuite {
  private def roundTrip[A: JsonRW: JsonRW2](name: String, input: String, output: String): Unit =
    test(s"$name JSON format matches JsonRW") {
      val value = input.parseJson2[A]
      val legacy = input.parseJson[A]
      assertEquals(value, legacy)
      assertEquals(value.toJson2(), legacy.toJson(spaces = 0))
      assertEquals(value.toJson2(), output)
      assertEquals(output.parseJson2[A], value)
    }

  roundTrip[Instant]("Instant", "\"2026-08-28T12:34:56.123456789Z\"", "\"2026-08-28T12:34:56.123456789Z\"")
  roundTrip[Duration]("Duration", "\"PT60S\"", "\"PT1M\"")
  roundTrip[Period]("Period", "\"P2W\"", "\"P14D\"")
  roundTrip[LocalDate]("LocalDate", "\"2024-02-29\"", "\"2024-02-29\"")
  roundTrip[LocalTime]("LocalTime", "\"12:34:00\"", "\"12:34\"")
  roundTrip[LocalDateTime]("LocalDateTime", "\"2026-08-28T12:34:56.1234\"", "\"2026-08-28T12:34:56.123400\"")
  roundTrip[OffsetDateTime]("OffsetDateTime", "\"2026-08-28T12:34:00+02:00\"", "\"2026-08-28T12:34+02:00\"")
  roundTrip[OffsetTime]("OffsetTime", "\"12:34:56-05:30\"", "\"12:34:56-05:30\"")
  roundTrip[ZonedDateTime](
    "ZonedDateTime with a named region",
    "\"2026-08-28T12:34:56+02:00[Europe/Berlin]\"",
    "\"2026-08-28T12:34:56+02:00[Europe/Berlin]\""
  )
  roundTrip[ZonedDateTime]("ZonedDateTime with a fixed offset", "\"2026-08-28T12:34:56Z\"", "\"2026-08-28T12:34:56Z\"")
  roundTrip[ZoneId]("ZoneId region", "\"Europe/Berlin\"", "\"Europe/Berlin\"")
  roundTrip[ZoneId]("ZoneId offset", "\"+02:00\"", "\"+02:00\"")
  roundTrip[ZoneOffset]("ZoneOffset", "\"+02:00\"", "\"+02:00\"")

  test("keep the target type and path for time values of the wrong JSON type") {
    val input = """{"date":123}"""
    val error = intercept[ParsingException](input.parseJson2[Map[String, LocalDate]])
    assertEquals(error.errors, Seq(ParseError("$.date", "should be LocalDate but it is Number", Some("123"))))
  }

  test("keep DateTimeParseException for malformed time strings") {
    val input = "\"2026-02-29\""
    val expected = intercept[DateTimeParseException](input.parseJson[LocalDate])
    val actual = intercept[DateTimeParseException](input.parseJson2[LocalDate])
    assertEquals(actual.getMessage, expected.getMessage)
  }
}
