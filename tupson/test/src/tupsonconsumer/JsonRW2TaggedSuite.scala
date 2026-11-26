package tupsonconsumer

import ba.sake.tupson.{JsonRW2, ParseError, ParsingException, TupsonException, discriminator}
import JsonRW2.*

class JsonRW2TaggedSuite extends munit.FunSuite {
  import JsonRW2TaggedSuite.*

  test("read a tagged subtype with the discriminator first or last") {
    val first = """{"@type":"Created","name":"backup","!id":42}"""
    val last = """{"name":"backup","!id":42,"@type":"Created"}"""
    val value: Event = Created("backup", 42)
    assertEquals(first.parseJson[Event], value)
    assertEquals(last.parseJson[Event], value)

    val deleted: Event = Deleted(42)
    assertEquals("""{"id":42,"@type":"Deleted"}""".parseJson[Event], deleted)
    assertEquals(deleted.toJson(), """{"@type":"Deleted","id":42}""")
  }

  test("write tagged fields directly and sort the tag with the other keys") {
    val value: Event = Created("backup", 42)
    assertEquals(value.toJson(), """{"@type":"Created","name":"backup","!id":42}""")
    assertEquals(value.toJson(sort = true), """{"!id":42,"@type":"Created","name":"backup"}""")
  }

  test("report subtype field errors at their original paths") {
    val error = intercept[ParsingException] {
      """{"event":{"name":false,"@type":"Created"}}""".parseJson[Envelope]
    }
    assertEquals(
      error.errors,
      Seq(
        ParseError("$.event.name", "should be String but it is Boolean", Some("false")),
        ParseError("$.event.!id", "is missing")
      )
    )
  }

  test("report missing, invalid, and unknown discriminator values") {
    val missing = intercept[ParsingException]("""{"id":42}""".parseJson[Event])
    assertEquals(missing.errors, Seq(ParseError("$.@type", "is missing")))

    val invalid = intercept[ParsingException]("""{"@type":null,"id":42}""".parseJson[Event])
    assertEquals(invalid.errors, Seq(ParseError("$.@type", "should be String but it is Null", Some("null"))))

    interceptMessage[TupsonException]("Subtype not found: 'Other'. Possible values: 'Created', 'Deleted'") {
      """{"@type":"Other","id":42}""".parseJson[Event]
    }
    interceptMessage[TupsonException]("JSON parsing exception") {
      """{"@type":"Other","id":[1,]}""".parseJson[Event]
    }
  }

  test("reject repeated discriminators regardless of their values") {
    Seq(
      """{"@type":"Deleted","id":42,"@type":"Deleted"}""",
      """{"@type":"Deleted","id":42,"@type":"Created"}""",
      """{"@type":null,"id":42,"@type":"Deleted"}"""
    ).foreach { input =>
      interceptMessage[TupsonException]("Duplicate JSON key at '$.@type'") {
        input.parseJson[Event]
      }
    }
  }

  test("read and write a custom discriminator") {
    val value: Command = Start("backup")
    val first = """{"kind":"Start","job":"backup"}"""
    val last = """{"job":"backup","kind":"Start"}"""
    assertEquals(first.parseJson[Command], value)
    assertEquals(last.parseJson[Command], value)
    assertEquals(value.toJson(), first)
    assertEquals(value.toJson(sort = true), last)

    val missing = intercept[ParsingException]("""{"@type":"Start","job":"backup"}""".parseJson[Command])
    assertEquals(missing.errors, Seq(ParseError("$.kind", "is missing")))

    interceptMessage[TupsonException]("Duplicate JSON key at '$.kind'") {
      """{"kind":"Start","job":"backup","kind":"Stop"}""".parseJson[Command]
    }
  }

  test("reject a custom discriminator that conflicts with a subtype field") {
    val errors = compileErrors("""
      @discriminator("kind")
      sealed trait Action derives JsonRW2
      case class Run(kind: String) extends Action
    """)
    assert(errors.contains("Discriminator 'kind' conflicts with a field"), errors)
  }
}

object JsonRW2TaggedSuite {
  sealed trait Event derives JsonRW2
  case class Created(name: String, `!id`: Int) extends Event
  case class Deleted(id: Int) extends Event
  case class Envelope(event: Event) derives JsonRW2

  @discriminator(name = "kind")
  sealed trait Command derives JsonRW2
  case class Start(job: String) extends Command
  case class Stop(job: String) extends Command
}
