package ba.sake.tupson

import org.typelevel.jawn.ast.*
import java.util.UUID
import java.time.*

class ParseSuite extends munit.FunSuite {

  test("parse primitives") {
    assertEquals("true".parseJson[Boolean], true)
    assertEquals("false".parseJson[Boolean], false)
    val ex = intercept[ParsingException] {
      """5""".parseJson[Boolean]
    }
    assertEquals(
      ex.errors.head,
      ParseError("$", "should be Boolean but it is Number", Some("5"))
    )

    // https://github.com/scala-js/scala-js/blob/v1.7.1/test-suite/shared/src/test/scala/org/scalajs/testsuite/javalib/lang/FloatTest.scala#L81-L85
    assertEquals("1.233".parseJson[Float], 1.233f)
    assertEquals("1233".parseJson[Float], 1233f)
    intercept[TupsonException] {
      """true""".parseJson[Float]
    }

    assertEquals("123".parseJson[Double], 123.0)
    assertEquals("1.23456789".parseJson[Double], 1.234_567_89d)

    assertEquals("1".parseJson[Int], 1)
    intercept[TupsonException] {
      """5.0""".parseJson[Int]
    }

    assertEquals("1".parseJson[Long], 1L)

    assertEquals(""" "" """.parseJson[String], "")
    assertEquals(""" "abc" """.parseJson[String], "abc")

    assertEquals(""" "a" """.parseJson[Char], 'a')
  }

  test("parse Long") {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER
    // TODO see if JS likes this..
    assertEquals("9007199254740991".parseJson[Long], 9007199254740991L)
    assertEquals("9007199254740992".parseJson[Long], 9007199254740992L)

    assertEquals("-9007199254740991".parseJson[Long], -9007199254740991L)
    assertEquals("-9007199254740992".parseJson[Long], -9007199254740992L)
  }

  test("parse JValue") {
    assertEquals(""" "aaa" """.parseJson[JValue], JString("aaa"))
    assertEquals(""" 123 """.parseJson[JValue], JNum("123"))
  }

  test("parse UUID") {
    assertEquals(
      """ "aff39af5-af24-43a8-a306-457a9f07b1b8" """.parseJson[UUID],
      UUID.fromString("aff39af5-af24-43a8-a306-457a9f07b1b8")
    )
  }

  test("parse Instant") {
    intercept[java.time.format.DateTimeParseException] {
      """ "bddsfsdf" """.parseJson[Instant]
    }
    assertEquals(""" "2023-10-20T13:47:11.504575Z" """.parseJson[Instant], Instant.parse("2023-10-20T13:47:11.504575Z"))
  }

  test("parse Duration") {
    intercept[java.time.format.DateTimeParseException] {
      """ "gn536435erfef" """.parseJson[Duration]
    }
    assertEquals(""" "PT168H" """.parseJson[Duration], Duration.ofDays(7))
    assertEquals(""" "PT42H" """.parseJson[Duration], Duration.ofHours(42))
    assertEquals(""" "PT33M" """.parseJson[Duration], Duration.ofMinutes(33))
    assertEquals(
      """ "PT26H3M4.005000006S" """.parseJson[Duration],
      Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4).plusMillis(5).plusNanos(6)
    )
  }

  //////////////////////////

  test("parse Seq") {
    assertEquals("[1,2,3]".parseJson[Seq[Int]], Seq(1, 2, 3))
    assertEquals("[1,2,3]".parseJson[Array[Int]].toSeq, Array(1, 2, 3).toSeq)

    val ex1 = intercept[ParsingException] {
      """5""".parseJson[Seq[Int]]
    }
    assertEquals(
      ex1.errors.head,
      ParseError("$", "should be Seq but it is Number", Some("5"))
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

  test("parse Option") {
    assertEquals("123".parseJson[Option[Int]], Option(123))
    assertEquals("null".parseJson[Option[Int]], Option.empty[Int])
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

  /* case class */
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
      ex1.errors.head,
      ParseError("$.integer", "is missing", None)
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

  /* sealed trait */
  test("parse sealed trait hierarchy") {
    import seal.*
    assertEquals(
      """{"str":"str","@type":"SealedCase1","integer":123}""".parseJson[SealedBase],
      SealedCase1("str", 123)
    )
  }

  /* enum */
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

  /* missing key -> default global value */
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

  /* missing key -> default "local" value */
  test("parse missing keys to their local defaults") {
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

  /* recursive data type */
  test("parse recursive data type") {
    import rec.*
    assertEquals(
      """{"children":[{"children":[]}]}""".parseJson[Node],
      Node(Seq(Node(Seq.empty)))
    )
  }

  /* weird key names */
  test("parse with weird key name") {
    import weird_named.*
    assertEquals(
      """{"weird named key":1}""".parseJson[WeirdNamed],
      WeirdNamed(1)
    )
  }

  /* named tuples */
  test("parse named tuple") {
    import namedTuples.given
    val str = """ { "name": "Mujo", "age": 35 } """
    val nt2 = str.parseJson[Person]
    assertEquals(nt2, (name = "Mujo", age = 35))
  }

  /* union type */
  test("parse union type") {
    import unionTypes.given
    assertEquals("1".parseJson[Int | String | Boolean], 1)
    assertEquals(""" "bla" """.parseJson[Int | String | Boolean], "bla")

    assertEquals(
      """{"str":"str","integer":123}""".parseJson[CaseClass1 | CaseClass2],
      CaseClass1("str", 123)
    )

    assertEquals(
      """{"bla":"c2","c1":{"str":"str","integer":123}}""".parseJson[CaseClass1 | CaseClass2],
      CaseClass2("c2", CaseClass1("str", 123))
    )

    // TODO no cookie
    /*assertEquals(
      """{ "x": 5 }""".parseJson[(x: Int) | (y: String)],
      (x = 5)
    )*/
  }

  // just testing the implicits work
  def bla: CaseClass1 = "".parseJson

}
