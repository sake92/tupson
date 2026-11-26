package ba.sake.tupson

import java.time.*
import java.time.format.DateTimeParseException

class TimeSuite extends munit.FunSuite {
  private def roundTrip[A: JsonRW](name: String, input: String, output: String): Unit =
    test(s"$name round-trip JSON format") {
      val value = input.parseJson[A]
      assertEquals(value.toJson(), output)
      assertEquals(output.parseJson[A], value)
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
    val error = intercept[ParsingException](input.parseJson[Map[String, LocalDate]])
    assertEquals(error.errors, Seq(ParseError("$.date", "should be LocalDate but it is Number", Some("123"))))
  }

  test("keep DateTimeParseException for malformed time strings") {
    val input = "\"2026-02-29\""
    intercept[DateTimeParseException](input.parseJson[LocalDate])
  }

  private def checkValue[A: JsonRW](name: String, value: A, json: String): Unit =
    test(s"$name round-trip value") {
      assertEquals(value.toJson(), json)
      assertEquals(json.parseJson[A], value)
    }

  checkValue("LocalDate", LocalDate.of(2026, 8, 28), "\"2026-08-28\"")
  checkValue("LocalTime", LocalTime.of(12, 34, 56), "\"12:34:56\"")
  checkValue(
    "LocalDateTime",
    LocalDateTime.of(2026, 8, 28, 12, 34, 56),
    "\"2026-08-28T12:34:56\""
  )
  checkValue(
    "OffsetDateTime",
    OffsetDateTime.of(2026, 8, 28, 12, 34, 56, 0, ZoneOffset.ofHours(2)),
    "\"2026-08-28T12:34:56+02:00\""
  )
  checkValue(
    "OffsetTime",
    OffsetTime.of(12, 34, 56, 0, ZoneOffset.ofHours(2)),
    "\"12:34:56+02:00\""
  )
  checkValue(
    "ZonedDateTime",
    ZonedDateTime.of(2026, 8, 28, 12, 34, 56, 0, ZoneOffset.UTC),
    "\"2026-08-28T12:34:56Z\""
  )
  checkValue("ZoneId region", ZoneId.of("Europe/Berlin"), "\"Europe/Berlin\"")
  checkValue("ZoneId offset", ZoneOffset.ofHours(2), "\"+02:00\"")
  checkValue("YearMonth", YearMonth.of(2026, 8), "\"2026-08\"")
  checkValue("MonthDay", MonthDay.of(8, 28), "\"--08-28\"")
  checkValue("Year", Year.of(2026), "\"2026\"")
  checkValue("Month", Month.AUGUST, "\"AUGUST\"")
  checkValue("DayOfWeek", DayOfWeek.SATURDAY, "\"SATURDAY\"")

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


  test("parse Instant") {
    intercept[java.time.format.DateTimeParseException] {
      """ "bddsfsdf" """.parseJson[Instant]
    }
    assertEquals(""" "2023-10-20T13:47:11.504575Z" """.parseJson[Instant], Instant.parse("2023-10-20T13:47:11.504575Z"))
  }

  test("parse Duration") {
    intercept[java.time.format.DateTimeParseException] {
      """ "gn536435erfef" """.parseJson[Duration]
    }
    assertEquals(""" "PT168H" """.parseJson[Duration], Duration.ofDays(7))
    assertEquals(""" "PT42H" """.parseJson[Duration], Duration.ofHours(42))
    assertEquals(""" "PT33M" """.parseJson[Duration], Duration.ofMinutes(33))
    assertEquals(
      """ "PT26H3M4.005000006S" """.parseJson[Duration],
      Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4).plusMillis(5).plusNanos(6)
    )
  }
}
