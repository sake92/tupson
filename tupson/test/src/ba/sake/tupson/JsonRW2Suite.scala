package ba.sake.tupson

import java.nio.charset.StandardCharsets.UTF_8
import ba.sake.tupson.{*, given}
import JsonRW2.{parseJson as parseJson2, toJson as toJson2}

class JsonRW2Suite extends munit.FunSuite {
  test("read scalar values with the same results as JsonRW") {
    def compare[A: JsonRW: JsonRW2](inputs: String*): Unit =
      inputs.foreach(input => assertEquals(input.parseJson2[A], input.parseJson[A]))

    compare[String]("\"\"", """ "ž 😃 \n \"" """)
    compare[Char]("\"a\"", "\"abc\"", "\"ž\"")
    compare[Boolean]("true", "false")
    compare[Int]("0", "-2147483648", "2147483647")
    compare[Long]("-9223372036854775808", "9223372036854775807", "9007199254740992")
    compare[Float]("1233", "1.233", "-2e-4")
    compare[Double]("123", "1.23456789", "-2e-4")
  }

  test("write scalar values that both codecs can read") {
    def roundTrip[A: JsonRW: JsonRW2](value: A): Unit = {
      val json = value.toJson2()
      assertEquals(json.parseJson[A], value)
      assertEquals(json.getBytes(UTF_8).parseJson2[A], value)
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
    val error = intercept[ParsingException]("5".parseJson2[Boolean])
    assertEquals(error.errors, Seq(ParseError("$", "should be Boolean but it is Number", Some("5"))))

    val nullValue = intercept[ParsingException]("null".parseJson2[Int])
    assertEquals(nullValue.errors, Seq(ParseError("$", "should be Int but it is Null", Some("null"))))
    intercept[ParsingException]("[null]".parseJson2[Int])

    val emptyChar = intercept[ParsingException]("\"\"".parseJson2[Char])
    assertEquals(emptyChar.errors, Seq(ParseError("$", "should be Char but it is String", Some("\"\""))))

    Seq("[1,]", "{\"a\":trux}", "true false").foreach { input =>
      val syntax = intercept[TupsonException](input.parseJson2[Boolean])
      assertEquals(syntax.getMessage, "JSON parsing exception")
    }
    intercept[TupsonException]("5.0".parseJson2[Int])
    intercept[TupsonException]("2147483648".parseJson2[Int])
  }

  test("read and write options with the same wire format as JsonRW") {
    Seq(" null ", "42").foreach { input =>
      assertEquals(input.parseJson2[Option[Int]], input.parseJson[Option[Int]])
    }

    val empty: Option[Int] = None
    val present: Option[Int] = Some(42)
    assertEquals(empty.toJson2(), "null")
    assertEquals(present.toJson2(), "42")

    val input = """{"empty":null,"present":42}"""
    val value = input.parseJson2[Map[String, Option[Int]]]
    assertEquals(value, input.parseJson[Map[String, Option[Int]]])
    assertEquals(value.toJson2(sort = true), input)
  }

  test("preserve element errors when reading an option") {
    val error = intercept[ParsingException] {
      """{"number":false}""".parseJson2[Map[String, Option[Int]]]
    }
    assertEquals(error.errors, Seq(ParseError("$.number", "should be Int but it is Boolean", Some("false"))))

    val syntax = intercept[TupsonException]("nulx".parseJson2[Option[Int]])
    assertEquals(syntax.getMessage, "JSON parsing exception")
  }

  test("read maps with the same values as JsonRW") {
    val inputs = Seq(
      "{}",
      """{"z":{"b":2,"a":1},"a":{},"escaped\"key":{"x":3}}""",
      """{"a":{"x":1},"a":{"x":2}}"""
    )

    inputs.foreach { input =>
      assertEquals(input.parseJson2[Map[String, Map[String, Int]]], input.parseJson[Map[String, Map[String, Int]]])
    }
  }

  test("write maps in iteration order by default and sort recursively on request") {
    val nested: Map[String, Int] = scala.collection.immutable.ListMap("b" -> 2, "a" -> 1)
    val value: Map[String, Map[String, Int]] = scala.collection.immutable.ListMap("z" -> nested, "a" -> Map.empty)
    assertEquals(value.toJson2(), """{"z":{"b":2,"a":1},"a":{}}""")
    assertEquals(value.toJson2(sort = true), """{"a":{},"z":{"a":1,"b":2}}""")
  }

  test("report a map value error at its key and validate remaining JSON") {
    val error = intercept[ParsingException] {
      """{"outer":{"ok":1,"bad":false,"alsoBad":true}}""".parseJson2[Map[String, Map[String, Int]]]
    }
    assertEquals(error.errors, Seq(ParseError("$.outer.bad", "should be Int but it is Boolean", Some("false"))))

    val syntax = intercept[TupsonException] {
      """{"bad":false,"extra":[1,]}""".parseJson2[Map[String, Int]]
    }
    assertEquals(syntax.getMessage, "JSON parsing exception")
  }

  test("map scalar codecs and preserve validation errors") {
    case class Email(value: String)
    val unchecked = JsonRW2[String].bimap(Email.apply)(_.value)
    assertEquals("\"ada\"".parseJson2[Email](using unchecked), Email("ada"))

    val email = JsonRW2[String].emap { value =>
      if value.contains('@') then Right(Email(value)) else Left("must contain @")
    }(_.value)
    val value = Email("ada@example.org")
    assertEquals(value.toJson2()(using email).parseJson2[Email](using email), value)

    val invalid = intercept[ParsingException]("\"ada\"".parseJson2[Email](using email))
    assertEquals(invalid.errors, Seq(ParseError("$", "must contain @")))
    val mismatch = intercept[ParsingException]("42".parseJson2[Email](using email))
    assertEquals(mismatch.errors.map(_.msg), Seq("should be String but it is Number"))
  }
}
