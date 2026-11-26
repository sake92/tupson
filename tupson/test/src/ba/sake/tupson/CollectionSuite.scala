package ba.sake.tupson

class CollectionSuite extends munit.FunSuite {

  test("parse Seq") {
    assertEquals("[1,2,3]".parseJson[Seq[Int]], Seq(1, 2, 3))
    assertEquals("[1,2,3]".parseJson[Array[Int]].toSeq, Array(1, 2, 3).toSeq)

    val ex1 = intercept[ParsingException] {
      """5""".parseJson[Seq[Int]]
    }
    assertEquals(
      ex1.errors,
      Seq(ParseError("$", "should be Seq but it is Number", Some("5")))
    )

    val ex2 = intercept[ParsingException] {
      """[ true, "" ]""".parseJson[Seq[Int]]
    }
    assertEquals(
      ex2.errors,
      Seq(
        ParseError("$[0]", "should be Int but it is Boolean", Some("true")),
        ParseError("$[1]", "should be Int but it is String", Some(""""""""))
      )
    )
  }

  test("write Seq") {
    assertEquals(Seq(1, 2, 3).toJson(spaces = 0), "[1,2,3]")
    assertEquals(Array(1, 2, 3).toJson(spaces = 0), "[1,2,3]")
  }

  test("parse Option") {
    assertEquals("123".parseJson[Option[Int]], Option(123))
    assertEquals("null".parseJson[Option[Int]], Option.empty[Int])
  }

  test("write Option") {
    assertEquals(Option(123).toJson(), "123")
    assertEquals(Option.empty[Int].toJson(), "null")
  }

  test("parse Map") {
    assertEquals(
      """{"a":"abc"}""".parseJson[Map[String, String]],
      Map("a" -> "abc")
    )
    assertEquals(
      """{"string":"abc"}""".parseJson[Map[String, String]],
      Map("string" -> "abc")
    )
    intercept[TupsonException] {
      // has to be an object
      """5""".parseJson[Map[String, String]]
    }
  }

  test("write Map") {
    assertEquals(Map("a" -> "abc").toJson(spaces = 0), """{"a":"abc"}""")
    assertEquals(
      Map("string" -> "abc").toJson(spaces = 0),
      """{"string":"abc"}"""
    )
  }

  test("preserve element errors when reading an option") {
    val error = intercept[ParsingException] {
      """{"number":false}""".parseJson[Map[String, Option[Int]]]
    }
    assertEquals(error.errors, Seq(ParseError("$.number", "should be Int but it is Boolean", Some("false"))))

    val syntax = intercept[TupsonException]("nulx".parseJson[Option[Int]])
    assertEquals(syntax.getMessage, "JSON parsing exception")
  }

  test("accumulate list errors with nested indices") {
    val input = """{"items":[[false,1],[true,null]]}"""
    val actual = intercept[ParsingException](input.parseJson[Map[String, List[List[Int]]]])
    assertEquals(actual.errors.map(_.path), Seq("$.items[0][0]", "$.items[1][0]", "$.items[1][1]"))

    Seq("[1,]", "[1").foreach { malformed =>
      val syntax = intercept[TupsonException](malformed.parseJson[List[Int]])
      assertEquals(syntax.getMessage, "JSON parsing exception")
    }
  }

  test("stop decoding array elements after ten document errors") {
    var decoded = 0
    case class Positive(value: Int)
    given JsonRW[Positive] = JsonRW[Int].emap { value =>
      decoded = decoded + 1
      if value > 0 then Right(Positive(value)) else Left("must be positive")
    }(_.value)

    val invalid = List.fill(5000)("-1").mkString("[", ",", "]")
    val input = s"[$invalid,$invalid]"
    val error = intercept[ParsingException](input.parseJson[List[List[Positive]]])
    assertEquals(decoded, 10)
    assertEquals(error.errors.map(_.path), (0 until 10).map(index => s"$$[0][$index]"))

    val malformed = List.fill(10)("-1").mkString("[", ",", ",[1,]]")
    val syntax = intercept[TupsonException](malformed.parseJson[List[Positive]])
    assertEquals(syntax.getMessage, "JSON parsing exception")
  }

  test("round-trip sequences, sets, and primitive and reference arrays") {
    def roundTrip[A: JsonRW](input: String, expected: A): Unit = {
      val value = input.parseJson[A]
      assertEquals(value, expected)
      assertEquals(value.toJson().parseJson[A], value)
    }

    roundTrip[Seq[Int]]("[3,1,3]", Seq(3, 1, 3))
    roundTrip[Set[Int]]("[3,1,3]", Set(3, 1))

    val ints = "[3,1,3]".parseJson[Array[Int]]
    assertEquals(ints.toList, List(3, 1, 3))
    assertEquals(ints.toJson(), "[3,1,3]")

    val json = """["z","a"]"""
    val strings = json.parseJson[Array[String]]
    assertEquals(strings.toList, List("z", "a"))
    assertEquals(strings.toJson(), json)
    assertEquals("[]".parseJson[Array[Int]].toList, Nil)
  }

  test("preserve indexed errors for sequences, sets, and arrays") {
    def compare[A: JsonRW](): Unit = {
      val input = "[false,1,null]"
      val actual = intercept[ParsingException](input.parseJson[A])
      assertEquals(actual.errors.map(_.path), Seq("$[0]", "$[2]"))
    }

    compare[Seq[Int]]()
    compare[Set[Int]]()
    compare[Array[Int]]()
  }

  test("reject repeated map keys even after an invalid value") {
    val json = """{"outer":{"a":false,"\u0061":2}}"""
    interceptMessage[TupsonException]("Duplicate JSON key at '$.outer.a'") {
      json.parseJson[Map[String, Map[String, Int]]]
    }
  }

  test("write maps in iteration order by default and sort recursively on request") {
    val nested: Map[String, Int] = scala.collection.immutable.ListMap("b" -> 2, "a" -> 1)
    val value: Map[String, Map[String, Int]] = scala.collection.immutable.ListMap("z" -> nested, "a" -> Map.empty)
    assertEquals(value.toJson(), """{"z":{"b":2,"a":1},"a":{}}""")
    assertEquals(value.toJson(sort = true), """{"a":{},"z":{"a":1,"b":2}}""")
  }

  test("report a map value error at its key and validate remaining JSON") {
    val error = intercept[ParsingException] {
      """{"outer":{"ok":1,"bad":false,"alsoBad":true}}""".parseJson[Map[String, Map[String, Int]]]
    }
    assertEquals(error.errors, Seq(ParseError("$.outer.bad", "should be Int but it is Boolean", Some("false"))))

    val syntax = intercept[TupsonException] {
      """{"bad":false,"extra":[1,]}""".parseJson[Map[String, Int]]
    }
    assertEquals(syntax.getMessage, "JSON parsing exception")
  }
}
