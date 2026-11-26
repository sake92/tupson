package ba.sake.tupson

import java.math.{BigDecimal, BigInteger}
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import ba.sake.tupson.{*, given}

class JsonRWSuite extends munit.FunSuite {
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

  test("preserve element errors when reading an option") {
    val error = intercept[ParsingException] {
      """{"number":false}""".parseJson[Map[String, Option[Int]]]
    }
    assertEquals(error.errors, Seq(ParseError("$.number", "should be Int but it is Boolean", Some("false"))))

    val syntax = intercept[TupsonException]("nulx".parseJson[Option[Int]])
    assertEquals(syntax.getMessage, "JSON parsing exception")
  }

  test("accumulate list errors with nested indices") {
    val input = """{"items":[[false,1],[true,null]]}"""
    val actual = intercept[ParsingException](input.parseJson[Map[String, List[List[Int]]]])
    assertEquals(actual.errors.map(_.path), Seq("$.items[0][0]", "$.items[1][0]", "$.items[1][1]"))

    Seq("[1,]", "[1").foreach { malformed =>
      val syntax = intercept[TupsonException](malformed.parseJson[List[Int]])
      assertEquals(syntax.getMessage, "JSON parsing exception")
    }
  }

  test("stop decoding array elements after ten document errors") {
    var decoded = 0
    case class Positive(value: Int)
    given JsonRW[Positive] = JsonRW[Int].emap { value =>
      decoded = decoded + 1
      if value > 0 then Right(Positive(value)) else Left("must be positive")
    }(_.value)

    val invalid = List.fill(5000)("-1").mkString("[", ",", "]")
    val input = s"[$invalid,$invalid]"
    val error = intercept[ParsingException](input.parseJson[List[List[Positive]]])
    assertEquals(decoded, 10)
    assertEquals(error.errors.map(_.path), (0 until 10).map(index => s"$$[0][$index]"))

    val malformed = List.fill(10)("-1").mkString("[", ",", ",[1,]]")
    val syntax = intercept[TupsonException](malformed.parseJson[List[Positive]])
    assertEquals(syntax.getMessage, "JSON parsing exception")
  }

  test("round-trip sequences, sets, and primitive and reference arrays") {
    def roundTrip[A: JsonRW](input: String, expected: A): Unit = {
      val value = input.parseJson[A]
      assertEquals(value, expected)
      assertEquals(value.toJson().parseJson[A], value)
    }

    roundTrip[Seq[Int]]("[3,1,3]", Seq(3, 1, 3))
    roundTrip[Set[Int]]("[3,1,3]", Set(3, 1))

    val ints = "[3,1,3]".parseJson[Array[Int]]
    assertEquals(ints.toList, List(3, 1, 3))
    assertEquals(ints.toJson(), "[3,1,3]")

    val json = """["z","a"]"""
    val strings = json.parseJson[Array[String]]
    assertEquals(strings.toList, List("z", "a"))
    assertEquals(strings.toJson(), json)
    assertEquals("[]".parseJson[Array[Int]].toList, Nil)
  }

  test("preserve indexed errors for sequences, sets, and arrays") {
    def compare[A: JsonRW](): Unit = {
      val input = "[false,1,null]"
      val actual = intercept[ParsingException](input.parseJson[A])
        assertEquals(actual.errors.map(_.path), Seq("$[0]", "$[2]"))
    }

    compare[Seq[Int]]()
    compare[Set[Int]]()
    compare[Array[Int]]()
  }

  test("reject repeated map keys even after an invalid value") {
    val json = """{"outer":{"a":false,"\u0061":2}}"""
    interceptMessage[TupsonException]("Duplicate JSON key at '$.outer.a'") {
      json.parseJson[Map[String, Map[String, Int]]]
    }
  }

  test("write maps in iteration order by default and sort recursively on request") {
    val nested: Map[String, Int] = scala.collection.immutable.ListMap("b" -> 2, "a" -> 1)
    val value: Map[String, Map[String, Int]] = scala.collection.immutable.ListMap("z" -> nested, "a" -> Map.empty)
    assertEquals(value.toJson(), """{"z":{"b":2,"a":1},"a":{}}""")
    assertEquals(value.toJson(sort = true), """{"a":{},"z":{"a":1,"b":2}}""")
  }

  test("report a map value error at its key and validate remaining JSON") {
    val error = intercept[ParsingException] {
      """{"outer":{"ok":1,"bad":false,"alsoBad":true}}""".parseJson[Map[String, Map[String, Int]]]
    }
    assertEquals(error.errors, Seq(ParseError("$.outer.bad", "should be Int but it is Boolean", Some("false"))))

    val syntax = intercept[TupsonException] {
      """{"bad":false,"extra":[1,]}""".parseJson[Map[String, Int]]
    }
    assertEquals(syntax.getMessage, "JSON parsing exception")
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
}
