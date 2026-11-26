package ba.sake.tupson

import ProductFixtures.*
import SumFixtures.*

class SumSuite extends munit.FunSuite {
  import SumSuite.*

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

  test("round-trip a case object as a tag-only object") {
    val input = """{"@type":"Stopped"}"""
    val value: Event = Stopped
    assertEquals(value.toJson(), input)
    assertEquals(value.toJson(sort = true), input)
    assert(input.parseJson[Event] eq Stopped)
    assert("""{"extra":[1,2],"@type":"Stopped"}""".parseJson[Event] eq Stopped)

    interceptMessage[TupsonException]("Duplicate JSON key at '$.@type'") {
      """{"@type":"Stopped","@type":"Stopped"}""".parseJson[Event]
    }
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

    interceptMessage[TupsonException]("Subtype not found: 'Other'. Possible values: 'Created', 'Deleted', 'Stopped'") {
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
      sealed trait Action derives JsonRW
      case class Run(kind: String) extends Action
    """)
    assert(errors.contains("Discriminator 'kind' conflicts with a field"), errors)
  }

  test("round-trip a recursive tagged sum with late tags at each level") {
    val leaf = """{"value":7,"@type":"Leaf"}"""
    val branch = s"""{"children":[$leaf],"@type":"Branch"}"""
    val input = s"""{"children":[$branch,$leaf],"@type":"Branch"}"""
    val tree: Tree = Branch(List(Branch(List(Leaf(7))), Leaf(7)))

    assertEquals(input.parseJson[Tree], tree)
    assertEquals(tree.toJson().parseJson[Tree], tree)
  }

  test("report indexed paths for errors inside recursive tagged sums") {
    val leaf = """{"value":false,"@type":"Leaf"}"""
    val branch = s"""{"children":[$leaf],"@type":"Branch"}"""
    val input = s"""{"children":[$branch],"@type":"Branch"}"""

    val error = intercept[ParsingException](input.parseJson[Tree])
    assertEquals(
      error.errors,
      Seq(ParseError("$.children[0].children[0].value", "should be Int but it is Boolean", Some("false")))
    )
  }

  test("use the type parameter's codec in generic tagged sums") {
    val number: Result[Int] = Success(42)
    val text: Result[String] = Success("backup")
    val numberJson = """{"@type":"Success","value":42}"""
    val textJson = """{"@type":"Success","value":"backup"}"""
    assertEquals(number.toJson(), numberJson)
    assertEquals(text.toJson(), textJson)
    assertEquals(numberJson.parseJson[Result[Int]], number)
    assertEquals(textJson.parseJson[Result[String]], text)

    val failure: Result[Int] = Failure("unavailable")
    val failureJson = """{"@type":"Failure","message":"unavailable"}"""
    assertEquals(failure.toJson(), failureJson)
    assertEquals(failureJson.parseJson[Result[Int]], failure)
    assertEquals(failureJson.parseJson[Result[String]], Failure("unavailable"))

    val mismatch = intercept[ParsingException](numberJson.parseJson[Result[String]])
    assertEquals(mismatch.errors, Seq(ParseError("$.value", "should be String but it is Number", Some("42"))))
  }

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

  test("parse sealed trait hierarchy") {
    import seal.*
    assertEquals(
      """{"str":"str","@type":"SealedCase1","integer":123}""".parseJson[SealedBase],
      SealedCase1("str", 123)
    )
  }

  test("write sealed trait hierarchy") {
    import seal.*
    val s1: SealedBase = SealedCase1("str", 123)
    assertEquals(
      s1.toJson(spaces = 0).parseJson[Json],
      """{"str":"str","@type":"SealedCase1","integer":123}""".parseJson[Json]
    )
    val s2: SealedBase = SealedCase2
    assertEquals(
      s2.toJson(spaces = 0),
      """{"@type":"SealedCase2"}"""
    )
  }

  test("parse singleton enum from string") {
    import enums.*

    assertEquals(
      """ "Red" """.parseJson[Semaphore],
      Semaphore.Red
    )
    assertEquals(
      """ "Green" """.parseJson[Color],
      Color.Green
    )
    interceptMessage[TupsonException](
      "Enum value not found: 'eeeeeee'. Possible values: 'Red', 'Green', 'Blue'"
    ) {
      """ "eeeeeee" """.parseJson[Color]
    }
  }

  test("parse enum ADT") {
    import enums.*

    assertEquals(
      """{"str":"str","@type":"Enum1Case","integer":123}""".parseJson[Enum1],
      Enum1.Enum1Case("str", Some(123))
    )
    assertEquals(
      """{"str":"str","@type":"Enum1Case","integer":null}""".parseJson[Enum1],
      Enum1.Enum1Case("str", None)
    )
    assertEquals(
      """{"@type":"eNum CaseD"}""".parseJson[Enum1],
      Enum1.`eNum CaseD`
    )
    interceptMessage[TupsonException](
      "Subtype not found: 'the-what'. Possible values: 'Enum1Case', 'Enum2Case', 'eNum CaseD'"
    ) {
      """ {"@type":"the-what"} """.parseJson[Enum1]
    }
  }

  test("parse annotated with discriminator") {
    import annotated.*

    assertEquals("""{ "tip":"A" }""".parseJson[Annot1], Annot1.A)
    assertEquals(
      """{ "tip":"B", "x": "abc" }""".parseJson[Annot1],
      Annot1.B("abc")
    )
    interceptMessage[TupsonException](
      "Subtype not found: 'the-what'. Possible values: 'A', 'B'"
    ) {
      """ {"tip":"the-what"} """.parseJson[Annot1]
    }
  }

  test("parse generic product and sum") {
    assertEquals("""{"value":1}""".parseJson[Gen[Int]], Gen(1))
    assertEquals("""{"gen":{"value":1}}""".parseJson[GenericBox[Int]], GenericBox(Gen(1)))
    assertEquals(
      """{"@type":"Const","value":1}""".parseJson[Expr[Int]],
      Const(1)
    )
    assertEquals(
      """{"@type":"Add","left":{"@type":"Const","value":1},"right":{"@type":"Const","value":2}}""".parseJson[Expr[Int]],
      Add(Const(1), Const(2))
    )

    // negative cases for discriminator
    val exMissing = intercept[ParsingException] {
      """{"value":1}""".parseJson[Expr[Int]]
    }
    assertEquals(
      exMissing.errors,
      Seq(ParseError("$.@type", "is missing", None))
    )

    interceptMessage[TupsonException](
      "Subtype not found: 'the-what'. Possible values: 'Const', 'Add'"
    ) {
      """ {"@type":"the-what"} """.parseJson[Expr[Int]]
    }
  }

  test("parse generic nested path-aware errors") {
    val ex = intercept[ParsingException] {
      """{"gen":{"value":"x"}}""".parseJson[GenericBox[Int]]
    }
    assertEquals(
      ex.errors,
      Seq(ParseError("$.gen.value", "should be Int but it is String", Some("\"x\"")))
    )

    val ex2 = intercept[ParsingException] {
      """{"@type":"Add","left":{"@type":"Const"},"right":{"@type":"Const","value":2}}""".parseJson[Expr[Int]]
    }
    assertEquals(
      ex2.errors,
      Seq(ParseError("$.left.value", "is missing", None))
    )

    val ex3 = intercept[ParsingException] {
      """{"@type":"Add","left":{"@type":"Const","value":"x"},"right":{"@type":"Const","value":2}}""".parseJson[Expr[Int]]
    }
    assertEquals(
      ex3.errors,
      Seq(ParseError("$.left.value", "should be Int but it is String", Some("\"x\"")))
    )
  }

  test("write singleton enum as string") {
    import enums.*
    val s1 = Semaphore.Red
    assertEquals(
      s1.toJson(),
      """ "Red" """.trim
    )

    val c1 = Color.Green
    assertEquals(
      c1.toJson(),
      """ "Green" """.trim
    )
  }

  test("write enum ADT") {
    import enums.*
    val s1: Enum1 = Enum1.Enum1Case("str", Some(123))
    val s2: Enum1 = Enum1.Enum1Case("str", None)
    val s3: Enum1 = Enum1.`eNum CaseD`
    assertEquals(
      s1.toJson(spaces = 0).parseJson[Json],
      """{"str":"str","@type":"Enum1Case","integer":123}""".parseJson[Json]
    )
    assertEquals(
      s2.toJson(spaces = 0).parseJson[Json],
      """{"str":"str","@type":"Enum1Case","integer":null}""".parseJson[Json]
    )
    assertEquals(
      s3.toJson(spaces = 0),
      """{"@type":"eNum CaseD"}"""
    )
  }

  test("write annotated with discriminator") {
    import annotated.*
    val a1 = Annot1.A
    val a2 = Annot1.B("abc")
    assertEquals(a1.toJson(spaces = 0), """{"tip":"A"}""")
    assertEquals(
      a2.toJson(spaces = 0).parseJson[Json],
      """{"x":"abc","tip":"B"}""".parseJson[Json]
    )
  }

  test("write nested hierarchy like flat type name") {
    import enums.*

    val buried1 = inner.buried.Inside.Abc
    assertEquals(
      buried1.toJson(),
      """"Abc""""
    )
  }

  test("write generic product and sum") {
    assertEquals(Gen(1).toJson(spaces = 0).parseJson[Json], """{"value":1}""".parseJson[Json])
    assertEquals(GenericBox(Gen(1)).toJson(spaces = 0).parseJson[Json], """{"gen":{"value":1}}""".parseJson[Json])
    val e: Expr[Int] = Add(Const(1), Const(2))
    assertEquals(
      e.toJson(spaces = 0).parseJson[Json],
      """{"@type":"Add","left":{"@type":"Const","value":1},"right":{"@type":"Const","value":2}}""".parseJson[Json]
    )
  }

  test("write generic sum with generic payload") {
    val e: Expr[Gen[Int]] = Add(Const(Gen(1)), Const(Gen(2)))
    assertEquals(
      e.toJson(spaces = 0).parseJson[Json],
      """{"@type":"Add","left":{"@type":"Const","value":{"value":1}},"right":{"@type":"Const","value":{"value":2}}}""".parseJson[Json]
    )
  }

}

object SumSuite {
  sealed trait Event derives JsonRW
  case class Created(name: String, `!id`: Int) extends Event
  case class Deleted(id: Int) extends Event
  case object Stopped extends Event
  case class Envelope(event: Event) derives JsonRW

  @discriminator(name = "kind")
  sealed trait Command derives JsonRW
  case class Start(job: String) extends Command
  case class Stop(job: String) extends Command

  sealed trait Tree derives JsonRW
  case class Branch(children: List[Tree]) extends Tree
  case class Leaf(value: Int) extends Tree

  sealed trait Result[+A] derives JsonRW
  case class Success[A](value: A) extends Result[A]
  case class Failure(message: String) extends Result[Nothing]

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

private object SumFixtures {
  object seal {
    sealed trait SealedBase derives JsonRW
    case class SealedCase1(str: String, integer: Int) extends SealedBase
    case object SealedCase2 extends SealedBase
  }

  object annotated {

    @discriminator("tip")
    enum Annot1 derives JsonRW:
      case A
      case B(x: String)
  }

  object enums {

    enum Semaphore derives JsonRW:
      case Red, Yellow, Green

    enum Color(val rgb: Int) derives JsonRW:
      case Red extends Color(0xff0000)
      case Green extends Color(0x00ff00)
      case Blue extends Color(0x0000ff)

    enum Enum1 derives JsonRW:
      case Enum1Case(str: String, integer: Option[Int])
      case Enum2Case()
      case `eNum CaseD`

    object inner {
      object buried {
        enum Inside derives JsonRW:
          case Abc
      }
    }
  }

  sealed trait Expr[T] derives JsonRW
  case class Const[T](value: T) extends Expr[T]
  case class Add[T](left: Expr[T], right: Expr[T]) extends Expr[T]
}
