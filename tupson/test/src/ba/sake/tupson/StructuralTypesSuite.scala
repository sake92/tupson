package ba.sake.tupson

class StructuralTypesSuite extends munit.FunSuite {
  import StructuralTypesSuite.*

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

  test("parse named tuple") {
    val str = """ { "name": "Mujo", "age": 35 } """
    val nt2 = str.parseJson[Person]
    assertEquals(nt2, (name = "Mujo", age = 35))
  }

  test("write named tuple") {
    val nt1: Person = (name = "Mujo", age = 35)
    assertEquals(
      nt1.toJson(spaces = 0).parseJson[Json],
      """{"age":35,"name":"Mujo"}""".parseJson[Json]
    )
  }

  test("parse literal types") {
    assertEquals("""{"x":"abc"}""".parseJson[LiteralPerson], (x = "abc"))
    assertEquals(
      """{"x":"abc"}""".parseJson[LiteralStringCaseClass],
      LiteralStringCaseClass("abc")
    )
    assertEquals("""{"x":123}""".parseJson[LiteralIntCaseClass], LiteralIntCaseClass(123))
    assertEquals(
      """{"x":true}""".parseJson[LiteralBooleanCaseClass],
      LiteralBooleanCaseClass(true)
    )
    assertEquals("""{"x":"a"}""".parseJson[LiteralCharCaseClass], LiteralCharCaseClass('a'))

    assertEquals(""" "abc" """.parseJson[LiteralUnion], "abc")
    assertEquals(""" 123 """.parseJson[LiteralUnion], 123)
    assertEquals(""" true """.parseJson[LiteralUnion], true)

    val ex = intercept[ParsingException] {
      """{"x":"def"}""".parseJson[LiteralStringCaseClass]
    }
    assertEquals(
      ex.errors,
      Seq(
        ParseError("$.x", "should be literal value 'abc'", Some("def"))
      )
    )

    val tupleEx = intercept[ParsingException] {
      """{"x":"def"}""".parseJson[LiteralPerson]
    }
    assertEquals(
      tupleEx.errors,
      Seq(
        ParseError("$.x", "should be literal value 'abc'", Some("def"))
      )
    )

    val charEx = intercept[ParsingException] {
      """{"x":"ab"}""".parseJson[LiteralCharCaseClass]
    }
    assertEquals(
      charEx.errors,
      Seq(
        ParseError("$.x", "should be literal value 'a'", Some("ab"))
      )
    )

    val unionEx = intercept[ParsingException] {
      """ false """.parseJson[LiteralUnion]
    }
    assertEquals(
      unionEx.errors,
      Seq(
        ParseError("$", "should be literal value 'true'", Some(false))
      )
    )
  }

  test("write literal types") {
    val nt: LiteralPerson = (x = "abc")
    assertEquals(nt.toJson(spaces = 0, sort = true), """{"x":"abc"}""")

    assertEquals(
      LiteralStringCaseClass("abc").toJson(spaces = 0, sort = true),
      """{"x":"abc"}"""
    )
    assertEquals(LiteralIntCaseClass(123).toJson(spaces = 0, sort = true), """{"x":123}""")
    assertEquals(
      LiteralBooleanCaseClass(true).toJson(spaces = 0, sort = true),
      """{"x":true}"""
    )
    assertEquals(LiteralCharCaseClass('a').toJson(spaces = 0, sort = true), """{"x":"a"}""")

    val unionString: LiteralUnion = "abc"
    assertEquals(unionString.toJson(spaces = 0, sort = true), """"abc"""")
    val unionInt: LiteralUnion = 123
    assertEquals(unionInt.toJson(spaces = 0, sort = true), "123")
    val unionBool: LiteralUnion = true
    assertEquals(unionBool.toJson(spaces = 0, sort = true), "true")
  }
}

object StructuralTypesSuite {
  type Person = (name: String, age: Int)
  type Choice = "start" | "stop"
  case class Envelope(details: (id: "fixed", person: Person)) derives JsonRW
  case class Fixed(name: "backup", attempt: 3, active: true, grade: 'a', total: 9L, ratio: 1.5f, weight: 2.25)
      derives JsonRW

  case class LiteralStringCaseClass(x: "abc") derives JsonRW
  case class LiteralIntCaseClass(x: 123) derives JsonRW
  case class LiteralBooleanCaseClass(x: true) derives JsonRW
  case class LiteralCharCaseClass(x: 'a') derives JsonRW

  type LiteralPerson = (x: "abc")
  type LiteralUnion = "abc" | 123 | true
}
