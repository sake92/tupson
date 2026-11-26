package tupsonconsumer

import ba.sake.tupson.{JsonRW2, ParseError, ParsingException, TupsonException}
import JsonRW2.*

class JsonRW2EnumSuite extends munit.FunSuite {
  import JsonRW2EnumSuite.*

  test("round-trip singleton enum cases as strings") {
    assertEquals(Status.Ready.toJson(), "\"Ready\"")
    assertEquals("\"Ready\"".parseJson[Status], Status.Ready)
    assertEquals("\"\\u0052eady\"".parseJson[Status], Status.Ready)
    assertEquals("\"Failed\"".parseJson[Status].toJson(), "\"Failed\"")

    assertEquals(Color.Green.toJson(), "\"Green\"")
    assertEquals("\"Green\"".parseJson[Color], Color.Green)
  }

  test("preserve enum errors for unknown names and wrong JSON types") {
    interceptMessage[TupsonException]("Enum value not found: 'Other'. Possible values: 'Ready', 'Failed'") {
      "\"Other\"".parseJson[Status]
    }

    val error = intercept[ParsingException]("""{"status":false}""".parseJson[Response])
    assertEquals(error.errors, Seq(ParseError("$.status", "should be String but it is Boolean", Some("false"))))
  }
}

object JsonRW2EnumSuite {
  enum Status derives JsonRW2 {
    case Ready, Failed
  }

  enum Color(val rgb: Int) derives JsonRW2 {
    case Green extends Color(0x00ff00)
    case Red extends Color(0xff0000)
  }

  case class Response(status: Status) derives JsonRW2
}
