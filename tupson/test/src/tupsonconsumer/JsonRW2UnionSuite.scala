package tupsonconsumer

import ba.sake.tupson.{JsonRW2, ParseError, ParsingException, TupsonException}
import JsonRW2.*

class JsonRW2UnionSuite extends munit.FunSuite {
  import JsonRW2UnionSuite.*

  test("read and write scalar unions by trying alternatives and dispatching by runtime type") {
    val number: Id = 42
    val text: Id = "42"
    assertEquals("42".parseJson[Id], number)
    assertEquals("\"42\"".parseJson[Id], text)
    assertEquals(number.toJson(), "42")
    assertEquals(text.toJson(), "\"42\"")
    assertEquals("\"backup\"".parseJson[String | Int], "backup")
    assertEquals("true".parseJson[Boolean | Int], true)

    val third: (Int | String) | Boolean = true
    assertEquals(third.toJson(), "true")
    assertEquals("true".parseJson[(Int | String) | Boolean], third)
  }

  test("compose a scalar union with a derived product") {
    val json = """{"id":"backup"}"""
    val value = JobId("backup")
    assertEquals(json.parseJson[JobId], value)
    assertEquals(value.toJson(), json)

    val error = intercept[ParsingException]("""{"id":false}""".parseJson[JobId])
    assertEquals(error.errors, Seq(ParseError("$.id", "should be String but it is Boolean", Some("false"))))
  }

  test("use mapped value types as union alternatives") {
    val value: Int | Email = Email("ada@example.org")
    val json = "\"ada@example.org\""
    assertEquals(json.parseJson[Int | Email], value)
    assertEquals(value.toJson(), json)
  }

  test("try overlapping scalar alternatives from left to right") {
    assert("42".parseJson[Int | Long].isInstanceOf[Int])
    assert("42".parseJson[Long | Int].isInstanceOf[Long])
    assertEquals("2147483648".parseJson[Int | Long], 2147483648L)
    assertEquals("1.25".parseJson[Int | Double], 1.25)
  }

  test("fall back between overlapping products without retaining rejected errors") {
    val value: First | Second = Second("backup")
    val json = """{"name":"backup"}"""
    assertEquals(json.parseJson[First | Second], value)
    assertEquals(value.toJson(), json)
    assertEquals("""{"id":42,"name":"backup"}""".parseJson[First | Second], First(42))

    val failed = intercept[ParsingException] {
      """{"before":false,"value":{}}""".parseJson[Envelope]
    }
    assertEquals(
      failed.errors,
      Seq(
        ParseError("$.before", "should be Int but it is Boolean", Some("false")),
        ParseError("$.value.name", "is missing")
      )
    )
  }

  test("fall back to tagged sums without sharing a reader mark") {
    val value: First | Event = Created(42)
    val json = """{"code":42,"@type":"Created"}"""
    assertEquals(json.parseJson[First | Event], value)
    assertEquals(value.toJson(), """{"@type":"Created","code":42}""")
  }

  test("do not use fallback to hide duplicate keys or malformed JSON") {
    interceptMessage[TupsonException]("Duplicate JSON key at '$.id'") {
      """{"id":1,"id":2,"name":"backup"}""".parseJson[First | Second]
    }
    interceptMessage[TupsonException]("JSON parsing exception") {
      """{"name":"backup","ignored":[1,]}""".parseJson[First | Second]
    }
  }
}

object JsonRW2UnionSuite {
  type Id = Int | String
  case class JobId(id: Id) derives JsonRW2
  case class First(id: Int) derives JsonRW2
  case class Second(name: String) derives JsonRW2
  case class Envelope(before: Int, value: First | Second) derives JsonRW2

  sealed trait Event derives JsonRW2
  case class Created(code: Int) extends Event

  case class Email(value: String)
  object Email {
    given JsonRW2[Email] = JsonRW2[String].bimap(Email.apply)(_.value)
  }
}
