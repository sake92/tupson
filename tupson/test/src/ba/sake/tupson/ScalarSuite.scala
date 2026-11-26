package ba.sake.tupson

import java.io.ByteArrayOutputStream
import java.math.{BigDecimal, BigInteger}
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

class ScalarSuite extends munit.FunSuite {

  test("parse primitives") {
    assertEquals("true".parseJson[Boolean], true)
    assertEquals("false".parseJson[Boolean], false)
    val ex = intercept[ParsingException] {
      """5""".parseJson[Boolean]
    }
    assertEquals(
      ex.errors,
      Seq(ParseError("$", "should be Boolean but it is Number", Some("5")))
    )

    // https://github.com/scala-js/scala-js/blob/v1.7.1/test-suite/shared/src/test/scala/org/scalajs/testsuite/javalib/lang/FloatTest.scala#L81-L85
    assertEquals("1.233".parseJson[Float], 1.233f)
    assertEquals("1233".parseJson[Float], 1233f)
    intercept[TupsonException] {
      """true""".parseJson[Float]
    }

    assertEquals("123".parseJson[Double], 123.0)
    assertEquals("1.23456789".parseJson[Double], 1.234_567_89d)

    assertEquals("1".parseJson[Int], 1)
    intercept[TupsonException] {
      """5.0""".parseJson[Int]
    }

    assertEquals("1".parseJson[Long], 1L)

    assertEquals(""" "" """.parseJson[String], "")
    assertEquals(""" "abc" """.parseJson[String], "abc")

    assertEquals(""" "a" """.parseJson[Char], 'a')
  }

  test("write primitives") {
    assertEquals(true.toJson(), "true")
    assertEquals(false.toJson(), "false")

    // https://github.com/scala-js/scala-js/blob/v1.7.1/test-suite/shared/src/test/scala/org/scalajs/testsuite/javalib/lang/FloatTest.scala#L81-L85
    assertEquals(1.233f.toJson().substring(0, 5), "1.233")

    assertEquals(1.234_567_89d.toJson(), "1.23456789")

    assertEquals(1.toJson(), "1")

    assertEquals(1L.toJson(), "1")

    assertEquals("".toJson(), "\"\"")
    assertEquals("abc".toJson(), "\"abc\"")

    assertEquals('a'.toJson(), "\"a\"")
  }

  test("parse Long") {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER
    // TODO see if JS likes this..
    assertEquals("9007199254740991".parseJson[Long], 9007199254740991L)
    assertEquals("9007199254740992".parseJson[Long], 9007199254740992L)

    assertEquals("-9007199254740991".parseJson[Long], -9007199254740991L)
    assertEquals("-9007199254740992".parseJson[Long], -9007199254740992L)
  }

  test("write Long") {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER
    // TODO see if JS likes this..
    assertEquals(9007199254740991L.toJson(), "9007199254740991")
    assertEquals(9007199254740992L.toJson(), "9007199254740992")

    assertEquals(-9007199254740991L.toJson(), "-9007199254740991")
    assertEquals(-9007199254740992L.toJson(), "-9007199254740992")

    Seq(Long.MinValue, Long.MaxValue).foreach { value =>
      val output = new ByteArrayOutputStream()
      value.writeJson(output, spaces = 0, sort = false)
      assertEquals(new String(output.toByteArray, UTF_8), value.toString)
    }
  }

  test("preserve UUID and URI wire formats") {
    def compare[A: JsonRW](json: String): Unit = {
      val value = json.parseJson[A]
      assertEquals(value.toJson(), json)
    }

    compare[UUID]("\"2d931510-d99f-494a-8c67-87feb05e1594\"")
    compare[URI]("\"https://example.com/a%20b?c=d\"")
    intercept[IllegalArgumentException]("\"bad-uuid\"".parseJson[UUID])
    val mismatch = intercept[ParsingException]("123".parseJson[URI])
    assertEquals(mismatch.errors, Seq(ParseError("$", "should be URI but it is Number", Some("123"))))
  }

  test("read and write exact decimals without rounding or losing scale") {
    val inputs = Seq("1e3", "1234567890.12345678901234567890", "1." + "0123456789" * 40)
    inputs.foreach { json =>
      val expected = new BigDecimal(json)
      val actual = json.parseJson[BigDecimal]
      assertEquals(actual, expected)
      assertEquals(actual.toJson(), expected.toString)
    }
  }

  test("preserve big integers and reject fraction syntax") {
    val inputs = Seq("42", "-9223372036854775809", "1234567890" * 40)
    inputs.foreach { json =>
      val value = json.parseJson[BigInteger]
      assertEquals(value, new BigInteger(json))
      assertEquals(value.toJson(), json)
    }

    Seq("1.5", "1e0").foreach { input =>
      intercept[NumberFormatException](input.parseJson[BigInteger])
    }
    interceptMessage[TupsonException]("JSON parsing exception") {
      "01".parseJson[BigInteger]
    }
  }

  test("round-trip scalar values from strings and UTF-8 bytes") {
    def roundTrip[A: JsonRW](value: A): Unit = {
      val json = value.toJson()
      assertEquals(json.parseJson[A], value)
      assertEquals(json.getBytes(UTF_8).parseJson[A], value)
    }

    roundTrip("ž 😃\n\"\\")
    roundTrip('ž')
    roundTrip(false)
    roundTrip(Int.MinValue)
    roundTrip(Long.MaxValue)
    roundTrip(1.233f)
    roundTrip(1.23456789d)
  }

  test("report type mismatches and reject malformed values") {
    val error = intercept[ParsingException]("5".parseJson[Boolean])
    assertEquals(error.errors, Seq(ParseError("$", "should be Boolean but it is Number", Some("5"))))

    val nullValue = intercept[ParsingException]("null".parseJson[Int])
    assertEquals(nullValue.errors, Seq(ParseError("$", "should be Int but it is Null", Some("null"))))
    intercept[ParsingException]("[null]".parseJson[Int])

    val emptyChar = intercept[ParsingException]("\"\"".parseJson[Char])
    assertEquals(emptyChar.errors, Seq(ParseError("$", "should be Char but it is String", Some("\"\""))))

    Seq("[1,]", "{\"a\":trux}", "true false").foreach { input =>
      val syntax = intercept[TupsonException](input.parseJson[Boolean])
      assertEquals(syntax.getMessage, "JSON parsing exception")
    }
    intercept[TupsonException]("5.0".parseJson[Int])
    intercept[TupsonException]("2147483648".parseJson[Int])
  }

  test("map scalar codecs and preserve validation errors") {
    case class Email(value: String)
    val unchecked = JsonRW[String].bimap(Email.apply)(_.value)
    assertEquals("\"ada\"".parseJson[Email](using unchecked), Email("ada"))

    val email = JsonRW[String].emap { value =>
      if value.contains('@') then Right(Email(value)) else Left("must contain @")
    }(_.value)
    val value = Email("ada@example.org")
    assertEquals(value.toJson()(using email).parseJson[Email](using email), value)

    val invalid = intercept[ParsingException]("\"ada\"".parseJson[Email](using email))
    assertEquals(invalid.errors, Seq(ParseError("$", "must contain @")))
    val mismatch = intercept[ParsingException]("42".parseJson[Email](using email))
    assertEquals(mismatch.errors.map(_.msg), Seq("should be String but it is Number"))
  }

  test("parse UUID") {
    assertEquals(
      """ "aff39af5-af24-43a8-a306-457a9f07b1b8" """.parseJson[UUID],
      UUID.fromString("aff39af5-af24-43a8-a306-457a9f07b1b8")
    )
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

  test("URI roundtrip") {
    assertEquals(URI.create("file:/sdfdsfsdf").toJson(spaces = 0), "\"file:/sdfdsfsdf\"")
    assertEquals("\"file:/sdfdsfsdf\"".parseJson[URI], URI.create("file:/sdfdsfsdf"))
    assertEquals(
      "\"https://example.com/a?b=c\"".parseJson[URI],
      URI.create("https://example.com/a?b=c")
    )
  }

  test("URI type mismatch") {
    val ex = intercept[ParsingException] {
      "123".parseJson[URI]
    }
    assertEquals(ex.errors, Seq(ParseError("$", "should be URI but it is Number", Some("123"))))
  }
}
