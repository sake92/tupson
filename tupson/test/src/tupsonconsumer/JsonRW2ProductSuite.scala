package tupsonconsumer

import ba.sake.tupson.{JsonRW2, ParseError, ParsingException, TupsonException}
import JsonRW2.*

class JsonRW2ProductSuite extends munit.FunSuite {
  import JsonRW2ProductSuite.*

  test("keep raw codec construction internal") {
    val errors = compileErrors("new ba.sake.tupson.JsonRW2[Int]() {}")
    assert(errors.contains("constructor JsonRW2 cannot be accessed"), errors)
  }

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

  test("default an absent list field and reject explicit null") {
    assertEquals("{}".parseJson[Batch], Batch(Nil))
    assertEquals("""{"items":[1,2]}""".parseJson[Batch], Batch(List(1, 2)))
    val error = intercept[ParsingException]("""{"items":null}""".parseJson[Batch])
    assertEquals(error.errors, Seq(ParseError("$.items", "should be List but it is Null", Some("null"))))
  }

  test("skip product fields after reaching the document error limit") {
    val invalid = List.fill(10)("false").mkString("[", ",", "]")
    val input = s"""{"batch":{"items":$invalid},"name":42}"""
    val error = intercept[ParsingException](input.parseJson[CappedProduct])
    assertEquals(error.errors.map(_.path), (0 until 10).map(index => s"$$.batch.items[$index]"))
  }

  test("default absent sequence, set, and array fields") {
    val collections = "{}".parseJson[Collections]
    assertEquals(collections.sequence, Seq.empty)
    assertEquals(collections.set, Set.empty)
    assertEquals(collections.array.toList, Nil)
    assertEquals(collections.toJson(), """{"sequence":[],"set":[],"array":[]}""")
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
  case class Batch(items: List[Int]) derives JsonRW2
  case class CappedProduct(batch: Batch, name: String, required: Int) derives JsonRW2
  case class Collections(sequence: Seq[Int], set: Set[Int], array: Array[Int]) derives JsonRW2
}
