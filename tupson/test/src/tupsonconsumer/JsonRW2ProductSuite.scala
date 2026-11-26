package tupsonconsumer

import ba.sake.tupson.{JsonRW2, ParseError, ParsingException, TupsonException}
import JsonRW2.*

class JsonRW2ProductSuite extends munit.FunSuite {
  import JsonRW2ProductSuite.*

  test("derive a product with constructor and codec defaults") {
    val input = """{"name":"backup","ignored":[null,{"ok":true}]}"""
    val job = Job("backup", 3, None, Map.empty)
    assertEquals(input.parseJson[Job], job)
    assertEquals(job.toJson(), """{"name":"backup","retries":3,"email":null,"limits":{}}""")
    assertEquals(job.toJson(sort = true), """{"email":null,"limits":{},"name":"backup","retries":3}""")
    assertEquals(job.toJson().parseJson[Job], job)
    assertEquals("""{"email":null}""".parseJson[DefaultEmail], DefaultEmail(None))
    assertEquals("{}".parseJson[DefaultEmail], DefaultEmail(Some("default")))
  }

  test("evaluate a computed constructor default only for an absent field") {
    var calls = 0
    def nextRetries(): Int = {
      calls = calls + 1
      calls
    }
    case class LocalJob(retries: Int = nextRetries()) derives JsonRW2

    assertEquals("""{"retries":9}""".parseJson[LocalJob].retries, 9)
    intercept[ParsingException]("""{"retries":null}""".parseJson[LocalJob])
    assertEquals(calls, 0)
    assertEquals("{}".parseJson[LocalJob].retries, 1)
    assertEquals("{}".parseJson[LocalJob].retries, 2)
  }

  test("accumulate missing and invalid fields at their paths") {
    val error = intercept[ParsingException] {
      """{"retries":false,"email":1}""".parseJson[Job]
    }
    assertEquals(
      error.errors,
      Seq(
        ParseError("$.retries", "should be Int but it is Boolean", Some("false")),
        ParseError("$.email", "should be String but it is Number", Some("1")),
        ParseError("$.name", "is missing")
      )
    )
    val syntax = intercept[TupsonException]("""{"name":"backup","ignored":[1,]}""".parseJson[Job])
    assertEquals(syntax.getMessage, "JSON parsing exception")
  }

  test("derive generic, nested and empty products") {
    val input = """{"inner":{"wire key":42,"fallback":null},"empty":{}}"""
    val value = Nest(Box(42), Empty())
    assertEquals(input.parseJson[Nest], value)
    assertEquals(value.toJson(), input)
    assertEquals("""{"wire key":"hello"}""".parseJson[Box[String]], Box("hello"))
  }

  test("use a custom value codec inside a derived product") {
    val input = """{"email":"ada@example.org"}"""
    val contact = Contact(Email("ada@example.org"))
    assertEquals(input.parseJson[Contact], contact)
    assertEquals(contact.toJson(), input)

    val error = intercept[ParsingException] {
      """{"email":"ada"}""".parseJson[Contact]
    }
    assertEquals(error.errors, Seq(ParseError("$.email", "must contain @")))
  }
}

object JsonRW2ProductSuite {
  case class Job(name: String, retries: Int = 3, email: Option[String], limits: Map[String, Int]) derives JsonRW2
  case class DefaultEmail(email: Option[String] = Some("default")) derives JsonRW2
  case class Box[A](`wire key`: A, fallback: Option[A] = None) derives JsonRW2
  case class Empty() derives JsonRW2
  case class Nest(inner: Box[Int], empty: Empty) derives JsonRW2

  case class Email(value: String)
  object Email {
    given JsonRW2[Email] = JsonRW2[String].emap { value =>
      if value.contains('@') then Right(Email(value)) else Left("must contain @")
    }(_.value)
  }
  case class Contact(email: Email) derives JsonRW2
}
