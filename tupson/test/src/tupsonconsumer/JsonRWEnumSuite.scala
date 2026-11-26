package tupsonconsumer

import ba.sake.tupson.{JsonRW, ParseError, ParsingException, TupsonException, discriminator}
import ba.sake.tupson.{parseJson, toJson}

class JsonRWEnumSuite extends munit.FunSuite {
  import JsonRWEnumSuite.*

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

  test("round-trip both singleton and payload cases of a mixed enum as tagged objects") {
    val idle: JobState = JobState.Idle
    val running: JobState = JobState.Running("backup", 2)
    val idleJson = """{"@type":"Idle"}"""
    val runningJson = """{"@type":"Running","job":"backup","attempt":2}"""
    assertEquals(idle.toJson(), idleJson)
    assert(idleJson.parseJson[JobState] eq JobState.Idle)
    assertEquals(running.toJson(), runningJson)
    assertEquals(runningJson.parseJson[JobState], running)
    assertEquals("""{"job":"backup","@type":"Running"}""".parseJson[JobState], JobState.Running("backup"))

    val error = intercept[ParsingException] {
      """{"job":false,"@type":"Running"}""".parseJson[JobState]
    }
    assertEquals(error.errors, Seq(ParseError("$.job", "should be String but it is Boolean", Some("false"))))
  }

  test("apply a custom discriminator to enum cases with payloads") {
    val value: Keyed = Keyed.Entry(42)
    val json = """{"kind":"Entry","id":42}"""
    assertEquals(value.toJson(), json)
    assertEquals(json.parseJson[Keyed], value)
  }
}

object JsonRWEnumSuite {
  enum Status derives JsonRW {
    case Ready, Failed
  }

  enum Color(val rgb: Int) derives JsonRW {
    case Green extends Color(0x00ff00)
    case Red extends Color(0xff0000)
  }

  case class Response(status: Status) derives JsonRW

  enum JobState derives JsonRW {
    case Idle
    case Running(job: String, attempt: Int = 1)
  }

  @discriminator("kind")
  enum Keyed derives JsonRW {
    case Entry(id: Int)
  }
}
