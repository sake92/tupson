package ba.sake.tupson

import ProductFixtures.*

class ProductSuite extends munit.FunSuite {
  import ProductSuite.*

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
    case class LocalJob(retries: Int = nextRetries()) derives JsonRW

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

    val duplicate = s"""{"batch":{"items":$invalid},"name":"a","name":"b"}"""
    interceptMessage[TupsonException]("Duplicate JSON key at '$.name'") {
      duplicate.parseJson[CappedProduct]
    }
  }

  test("reject repeated product fields") {
    interceptMessage[TupsonException]("Duplicate JSON key at '$.name'") {
      """{"name":"backup","name":"backup"}""".parseJson[Job]
    }
  }

  test("default absent sequence, set, and array fields") {
    val collections = "{}".parseJson[Collections]
    assertEquals(collections.sequence, Seq.empty)
    assertEquals(collections.set, Set.empty)
    assertEquals(collections.array.toList, Nil)
    assertEquals(collections.toJson(), """{"sequence":[],"set":[],"array":[]}""")
  }

  test("derive a recursive product and preserve nested error paths") {
    val input = """{"value":1,"children":[{"value":2}]}"""
    val tree = Node(1, List(Node(2, Nil)))
    assertEquals(input.parseJson[Node], tree)
    assertEquals(tree.toJson().parseJson[Node], tree)

    val error = intercept[ParsingException] {
      """{"value":1,"children":[{"value":false},{}]}""".parseJson[Node]
    }
    assertEquals(error.errors.map(_.path), Seq("$.children[0].value", "$.children[1].value"))
  }

  test("derive mutually recursive product codecs") {
    val value = Parent(Some(Child(None)))
    val input = """{"child":{"parent":null}}"""
    assertEquals(value.toJson(), input)
    assertEquals(input.parseJson[Parent], value)
  }

  test("parse case class") {
    assertEquals(
      """{"str":"str","integer":123}""".parseJson[CaseClass1],
      CaseClass1("str", 123)
    )

    assertEquals(
      """{"bla":"c2","c1":{"str":"str","integer":123}}""".parseJson[CaseClass2],
      CaseClass2("c2", CaseClass1("str", 123))
    )

    val ex1 = intercept[ParsingException] {
      """{"str":"str"}""".parseJson[CaseClass1]
    }
    assertEquals(
      ex1.errors,
      Seq(ParseError("$.integer", "is missing", None))
    )

    intercept[TupsonException] {
      // has to be an object
      """5""".parseJson[CaseClass1]
    }
    val ex2 = intercept[ParsingException] {
      """{ "str":123 }""".parseJson[CaseClass1]
    }
    assertEquals(
      ex2.errors,
      Seq(
        ParseError("$.str", "should be String but it is Number", Some("123")),
        ParseError("$.integer", "is missing", None)
      )
    )

    val ex3 = intercept[ParsingException] {
      """{"bla":"str", "c1": { "str":123 }}""".parseJson[CaseClass2]
    }
    assertEquals(
      ex3.errors,
      Seq(
        ParseError("$.c1.str", "should be String but it is Number", Some("123")),
        ParseError("$.c1.integer", "is missing", None)
      )
    )
  }

  test("write case class") {
    assertEquals(
      CaseClass1("str", 123).toJson(spaces = 0).parseJson[Json],
      """{"str":"str","integer":123}""".parseJson[Json]
    )

    assertEquals(
      CaseClass2("c2", CaseClass1("str", 123)).toJson(spaces = 0).parseJson[Json],
      """{"bla":"c2","c1":{"str":"str","integer":123}}""".parseJson[Json]
    )
  }

  test("parse recursive data type") {
    import rec.*
    assertEquals(
      """{"children":[{"children":[]}]}""".parseJson[Node],
      Node(Seq(Node(Seq.empty)))
    )
  }

  test("write recursive data type") {
    import rec.*
    val n1 = Node(Seq(Node(Seq.empty)))
    assertEquals(
      n1.toJson(spaces = 0),
      """{"children":[{"children":[]}]}"""
    )
  }

  test("parse with weird key name") {
    import weird_named.*
    assertEquals(
      """{"weird named key":1}""".parseJson[WeirdNamed],
      WeirdNamed(1)
    )
  }

  test("write with weird key name") {
    import weird_named.*
    val r1 = WeirdNamed(1)
    assertEquals(
      r1.toJson(spaces = 0),
      """{"weird named key":1}"""
    )
  }

  test("parse missing keys to their global defaults") {
    assertEquals(
      """{}""".parseJson[CaseClassOpt],
      CaseClassOpt(None, Seq.empty, Map.empty)
    )
    assertEquals(
      """{ "str": null, "seq": [], "map": {} }""".parseJson[CaseClassOpt],
      CaseClassOpt(None, Seq.empty, Map.empty)
    )
    assertEquals(
      """{ "str": "value", "seq": ["abc"], "map": {"a": "aaa"} }""".parseJson[CaseClassOpt],
      CaseClassOpt(Some("value"), Seq("abc"), Map("a" -> "aaa"))
    )
  }

  test("parse missing keys to their local defaults") {
    case class Bla(x: String = "deflt") derives JsonRW

    assertEquals(
      """{}""".parseJson[Bla],
      Bla("deflt")
    )
    assertEquals(
      """{}""".parseJson[CaseClassDefault],
      CaseClassDefault(Seq.empty, Some("default"))
    )
    assertEquals(
      """{ "lst": ["value"], "str": "string" }""".parseJson[CaseClassDefault],
      CaseClassDefault(Seq("value"), Some("string"))
    )
    assertEquals(
      """{ "lst": ["value"], "str": null }""".parseJson[CaseClassDefault],
      CaseClassDefault(Seq("value"), None)
    )
    intercept[TupsonException] {
      // "lst" must not be null
      assertEquals(
        """{ "lst": null }""".parseJson[CaseClassDefault],
        CaseClassDefault(Seq.empty, None)
      )
    }
  }

}

object ProductSuite {
  case class Job(name: String, retries: Int = 3, email: Option[String], limits: Map[String, Int]) derives JsonRW
  case class DefaultEmail(email: Option[String] = Some("default")) derives JsonRW
  case class Box[A](`wire key`: A, fallback: Option[A] = None) derives JsonRW
  case class Empty() derives JsonRW
  case class Nest(inner: Box[Int], empty: Empty) derives JsonRW

  case class Email(value: String)
  object Email {
    given JsonRW[Email] = JsonRW[String].emap { value =>
      if value.contains('@') then Right(Email(value)) else Left("must contain @")
    }(_.value)
  }
  case class Contact(email: Email) derives JsonRW
  case class Batch(items: List[Int]) derives JsonRW
  case class CappedProduct(batch: Batch, name: String, required: Int) derives JsonRW
  case class Collections(sequence: Seq[Int], set: Set[Int], array: Array[Int]) derives JsonRW
  case class Node(value: Int, children: List[Node]) derives JsonRW
  case class Parent(child: Option[Child]) derives JsonRW
  case class Child(parent: Option[Parent]) derives JsonRW
}

private object ProductFixtures {
  case class CaseClass1(str: String, integer: Int) derives JsonRW
  case class CaseClass2(bla: String, c1: CaseClass1) derives JsonRW

  case class CaseClassOpt(str: Option[String], seq: Seq[String], map: Map[String, String]) derives JsonRW
  case class CaseClassDefault(
      // parsed as Seq.empty IF THE KEY IS MISSING (not failing)
      lst: Seq[String] = Seq.empty,

      // parsed as Some("default") IF THE KEY IS MISSING (not failing)
      str: Option[String] = Some("default")
  ) derives JsonRW
  object rec {
    case class Node(children: Seq[Node]) derives JsonRW
  }

  object weird_named {
    case class WeirdNamed(`weird named key`: Int) derives JsonRW
  }

  case class Gen[T](value: T) derives JsonRW
  case class GenericBox[T](gen: Gen[T]) derives JsonRW
}
