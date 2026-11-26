package tupsonconsumer

import ba.sake.tupson.{JsonRW, ParseError, ParsingException, TupsonException}
import ba.sake.tupson.{parseJson, toJson}

class JsonRWStructuralSuite extends munit.FunSuite {
  import JsonRWStructuralSuite.*

  test("round-trip literal fields and reject other values") {
    val json = """{"name":"backup","attempt":3,"active":true,"grade":"a","total":9,"ratio":1.5,"weight":2.25}"""
    val value = Fixed("backup", 3, true, 'a', 9L, 1.5f, 2.25)
    assertEquals(json.parseJson[Fixed], value)
    assertEquals(value.toJson(), json)

    val invalid = """{"name":"wrong","attempt":3,"active":true,"grade":"ab","total":9,"ratio":1.5,"weight":2.25}"""
    val error = intercept[ParsingException](invalid.parseJson[Fixed])
    assertEquals(
      error.errors,
      Seq(
        ParseError("$.name", "should be literal value 'backup'", Some("wrong")),
        ParseError("$.grade", "should be literal value 'a'", Some("ab"))
      )
    )
  }

  test("use literal types in unions") {
    val first: Choice = "start"
    val second: Choice = "stop"
    assertEquals("\"start\"".parseJson[Choice], first)
    assertEquals("\"stop\"".parseJson[Choice], second)
    assertEquals(second.toJson(), "\"stop\"")
    assertEquals("42".parseJson[42 | "start"], 42)
  }

  test("read named tuples from JSON objects and sort only when requested") {
    val value: Person = (name = "Ada", age = 42)
    val input = """{"age":42,"extra":[true],"name":"Ada"}"""
    assertEquals(input.parseJson[Person], value)
    assertEquals(value.toJson(), """{"name":"Ada","age":42}""")
    assertEquals(value.toJson(sort = true), """{"age":42,"name":"Ada"}""")

    val nested = Envelope((id = "fixed", person = value))
    val json = """{"details":{"id":"fixed","person":{"name":"Ada","age":42}}}"""
    assertEquals(json.parseJson[Envelope], nested)
    assertEquals(nested.toJson(), json)
  }

  test("require named tuple keys and preserve field paths") {
    val missing = intercept[ParsingException]("{}".parseJson[(value: Option[Int])])
    assertEquals(missing.errors, Seq(ParseError("$.value", "is missing")))
    assertEquals("""{"value":null}""".parseJson[(value: Option[Int])].value, None)

    val invalid = intercept[ParsingException] {
      """{"details":{"id":"changed","person":{"name":"Ada","age":42}}}""".parseJson[Envelope]
    }
    assertEquals(invalid.errors, Seq(ParseError("$.details.id", "should be literal value 'fixed'", Some("changed"))))

    interceptMessage[TupsonException]("Duplicate JSON key at '$.age'") {
      """{"name":"Ada","age":42,"age":42}""".parseJson[Person]
    }
  }
}

object JsonRWStructuralSuite {
  type Person = (name: String, age: Int)
  type Choice = "start" | "stop"
  case class Envelope(details: (id: "fixed", person: Person)) derives JsonRW
  case class Fixed(name: "backup", attempt: 3, active: true, grade: 'a', total: 9L, ratio: 1.5f, weight: 2.25)
      derives JsonRW
}
