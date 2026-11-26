package tupsonconsumer

import scala.collection.immutable.VectorMap
import ba.sake.tupson.{Json, JsonRW2, TupsonException}
import JsonRW2.*

class JsonRW2DynamicSuite extends munit.FunSuite {
  import JsonRW2DynamicSuite.*

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
}

object JsonRW2DynamicSuite {
  case class Event(kind: String, data: Json) derives JsonRW2
}
