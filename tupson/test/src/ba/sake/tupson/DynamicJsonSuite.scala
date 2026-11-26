package ba.sake.tupson

import scala.collection.immutable.VectorMap

class DynamicJsonSuite extends munit.FunSuite {
  import DynamicJsonSuite.*

  test("round-trip dynamic values inside a typed product") {
    val input = """{"kind":"event","data":{"z":[null,true,"ž"],"a":-0.00e+02}}"""
    val data = Json.Obj(VectorMap(
      "z" -> Json.Arr(Vector(Json.Null, Json.Bool(true), Json.Str("ž"))),
      "a" -> Json.Num("-0.00e+02")
    ))
    val event = Event("event", data)
    assertEquals(input.parseJson[Event], event)
    assertEquals(event.toJson(), input)
    assertEquals(event.toJson(sort = true), """{"data":{"a":-0.00e+02,"z":[null,true,"ž"]},"kind":"event"}""")
  }

  test("keep numeric lexemes, including numbers outside BigDecimal's exponent range") {
    val raw = "1e2147483648"
    val value: Json = raw.parseJson[Json]
    assertEquals(value, Json.Num(raw))
    assertEquals(value.toJson(), raw)
  }

  test("reject malformed input, invalid numeric nodes, and repeated dynamic keys") {
    Seq("01", "1e", """{"a":[1,]}""").foreach { input =>
      interceptMessage[TupsonException]("JSON parsing exception") {
        input.parseJson[Json]
      }
    }
    intercept[IllegalArgumentException](Json.Num("""0,"extra":1"""))
    interceptMessage[TupsonException]("Duplicate JSON key at '$[0].a'") {
      """[{"a":1,"a":2}]""".parseJson[Json]
    }
  }

  test("parse Json") {
    assertEquals(""" "aaa" """.parseJson[Json], Json.Str("aaa"))
    assertEquals(""" 123 """.parseJson[Json], Json.Num("123"))
  }

  test("write sorted output") {
    val value = """{"b":1,"a":{"d":4,"c":3},"arr":[{"f":6,"e":5}]}""".parseJson[Json]
    assertEquals(
      value.toJson(spaces = 2, sort = true),
      """{
        |  "a": {
        |    "c": 3,
        |    "d": 4
        |  },
        |  "arr": [
        |    {
        |      "e": 5,
        |      "f": 6
        |    }
        |  ],
        |  "b": 1
        |}""".stripMargin
    )
    assertEquals(
      value.toJson(spaces = 0, sort = true),
      """{"a":{"c":3,"d":4},"arr":[{"e":5,"f":6}],"b":1}"""
    )
    assertEquals(
      value.toJson(spaces = -1, sort = true),
      """{"a":{"c":3,"d":4},"arr":[{"e":5,"f":6}],"b":1}"""
    )
  }

}

object DynamicJsonSuite {
  case class Event(kind: String, data: Json) derives JsonRW
}
