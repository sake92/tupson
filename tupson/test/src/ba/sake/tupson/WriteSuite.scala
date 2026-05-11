package ba.sake.tupson

import org.typelevel.jawn.ast.JValue

class WriteSuite extends munit.FunSuite {

  test("write primitives") {
    assertEquals(true.toJson, "true")
    assertEquals(false.toJson, "false")

    // https://github.com/scala-js/scala-js/blob/v1.7.1/test-suite/shared/src/test/scala/org/scalajs/testsuite/javalib/lang/FloatTest.scala#L81-L85
    assertEquals(1.233f.toJson.substring(0, 5), "1.233")

    assertEquals(1.234_567_89d.toJson, "1.23456789")

    assertEquals(1.toJson, "1")

    assertEquals(1L.toJson, "1")

    assertEquals("".toJson, "\"\"")
    assertEquals("abc".toJson, "\"abc\"")

    assertEquals('a'.toJson, "\"a\"")
  }

  test("write Long") {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER
    // TODO see if JS likes this..
    assertEquals(9007199254740991L.toJson, "9007199254740991")
    assertEquals(9007199254740992L.toJson, "9007199254740992")

    assertEquals(-9007199254740991L.toJson, "-9007199254740991")
    assertEquals(-9007199254740992L.toJson, "-9007199254740992")
  }

  test("write Seq") {
    assertEquals(Seq(1, 2, 3).toJson(spaces = 0), "[1,2,3]")
    assertEquals(Array(1, 2, 3).toJson(spaces = 0), "[1,2,3]")
  }

  test("write Option") {
    assertEquals(Option(123).toJson, "123")
    assertEquals(Option.empty[Int].toJson, "null")
  }

  test("write Map") {
    assertEquals(Map("a" -> "abc").toJson(spaces = 0), """{"a":"abc"}""")
    assertEquals(
      Map("string" -> "abc").toJson(spaces = 0),
      """{"string":"abc"}"""
    )
  }

  /* case class */
  test("write case class") {
    assertEquals(
      CaseClass1("str", 123).toJson(spaces = 0).parseJson[JValue],
      """{"str":"str","integer":123}""".parseJson[JValue]
    )

    assertEquals(
      CaseClass2("c2", CaseClass1("str", 123)).toJson(spaces = 0).parseJson[JValue],
      """{"bla":"c2","c1":{"str":"str","integer":123}}""".parseJson[JValue]
    )
  }

  /* sealed trait */
  test("write sealed trait hierarchy") {
    import seal.*
    val s1: SealedBase = SealedCase1("str", 123)
    assertEquals(
      s1.toJson(spaces = 0).parseJson[JValue],
      """{"str":"str","@type":"SealedCase1","integer":123}""".parseJson[JValue]
    )
    val s2: SealedBase = SealedCase2
    assertEquals(
      s2.toJson(spaces = 0),
      """{"@type":"SealedCase2"}"""
    )
  }

  /* enum */
  test("write singleton enum as string") {
    import enums.*
    val s1 = Semaphore.Red
    assertEquals(
      s1.toJson,
      """ "Red" """.trim
    )

    val c1 = Color.Green
    assertEquals(
      c1.toJson,
      """ "Green" """.trim
    )
  }

  test("write enum ADT") {
    import enums.*
    val s1: Enum1 = Enum1.Enum1Case("str", Some(123))
    val s2: Enum1 = Enum1.Enum1Case("str", None)
    val s3: Enum1 = Enum1.`eNum CaseD`
    assertEquals(
      s1.toJson(spaces = 0).parseJson[JValue],
      """{"str":"str","@type":"Enum1Case","integer":123}""".parseJson[JValue]
    )
    assertEquals(
      s2.toJson(spaces = 0).parseJson[JValue],
      """{"str":"str","@type":"Enum1Case","integer":null}""".parseJson[JValue]
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
      a2.toJson(spaces = 0).parseJson[JValue],
      """{"x":"abc","tip":"B"}""".parseJson[JValue]
    )
  }

  test("write nested hierarchy like flat type name") {
    import enums.*

    val burried1 = inner.burried.Inside.Abc
    assertEquals(
      burried1.toJson,
      """"Abc""""
    )

    /* no worky yet
    val burried2 = (new inner.instance).Inside.Def
    assertEquals(
      burried2.toJson,
      """{"@type":"Def"}"""
    )
     */
  }

  /* recursive data type */
  test("write recursive data type") {
    import rec.*
    val n1 = Node(Seq(Node(Seq.empty)))
    assertEquals(
      n1.toJson(spaces = 0),
      """{"children":[{"children":[]}]}"""
    )
  }

  /* weird key names */
  test("write with weird key name") {
    import weird_named.*
    val r1 = WeirdNamed(1)
    assertEquals(
      r1.toJson(spaces = 0),
      """{"weird named key":1}"""
    )
  }

  /* union type */
  test("write union type") {
    locally {
      val value: Int | String = 1
      assertEquals(value.toJson, """1""")
    }
    locally {
      val value: Int | String = "bla"
      assertEquals(value.toJson, """ "bla" """.trim)
    }
    locally {
      val value: CaseClass1 | CaseClass2 = CaseClass1("str", 123)
      assertEquals(
        value.toJson(spaces = 0).parseJson[JValue],
        """{"str":"str","integer":123}""".parseJson[JValue]
      )
    }
    locally {
      val value: CaseClass1 | CaseClass2 | Int = CaseClass2("c2", CaseClass1("str", 123))
      assertEquals(
        value.toJson(spaces = 0).parseJson[JValue],
        """{"bla":"c2","c1":{"str":"str","integer":123}}""".parseJson[JValue]
      )
    }
  }

  test("write named tuple") {
    val nt1: Person = (name = "Mujo", age = 35)
    assertEquals(
      nt1.toJson(spaces = 0).parseJson[JValue],
      """{"age":35,"name":"Mujo"}""".parseJson[JValue]
    )
  }

  test("write literal types") {
    val nt: LiteralPerson = (x = "abc")
    assertEquals(nt.toJson(spaces = 0, sort = true), """{"x":"abc"}""")

    assertEquals(
      LiteralStringCaseClass("abc").toJson(spaces = 0, sort = true),
      """{"x":"abc"}"""
    )
    assertEquals(LiteralIntCaseClass(123).toJson(spaces = 0, sort = true), """{"x":123}""")
    assertEquals(
      LiteralBooleanCaseClass(true).toJson(spaces = 0, sort = true),
      """{"x":true}"""
    )
    assertEquals(LiteralCharCaseClass('a').toJson(spaces = 0, sort = true), """{"x":"a"}""")

    val unionString: LiteralUnion = "abc"
    assertEquals(unionString.toJson(spaces = 0, sort = true), """"abc"""")
    val unionInt: LiteralUnion = 123
    assertEquals(unionInt.toJson(spaces = 0, sort = true), "123")
    val unionBool: LiteralUnion = true
    assertEquals(unionBool.toJson(spaces = 0, sort = true), "true")
  }

  test("write default spaced output") {
    assertEquals(
      CaseClass1("str", 123).toJson,
      """{
        |  "integer": 123,
        |  "str": "str"
        |}""".stripMargin
    )
    assertEquals(
      CaseClass1("str", 123).toJson(spaces = 0),
      """{"integer":123,"str":"str"}"""
    )
  }

  test("write custom spaced output") {
    assertEquals(
      Seq(CaseClass1("str", 123), CaseClass1("x", 456)).toJson(spaces = 4),
      """[
        |    {
        |        "integer": 123,
        |        "str": "str"
        |    },
        |    {
        |        "integer": 456,
        |        "str": "x"
        |    }
        |]""".stripMargin
    )
  }

  test("write explicit unsorted output") {
    assertEquals(
      CaseClass1("str", 123).toJson(sort = false),
      """{
        |  "str": "str",
        |  "integer": 123
        |}""".stripMargin
    )
    assertEquals(
      CaseClass1("str", 123).toJson(spaces = 0, sort = false),
      """{"str":"str","integer":123}"""
    )
  }

  test("write sorted output") {
    val value = """{"b":1,"a":{"d":4,"c":3},"arr":[{"f":6,"e":5}]}""".parseJson[JValue]
    assertEquals(
      value.toJson(sort = true),
      """{
        |  "a": {
        |    "c": 3,
        |    "d": 4
        |  },
        |  "arr": [
        |    {
        |      "e": 5,
        |      "f": 6
        |    }
        |  ],
        |  "b": 1
        |}""".stripMargin
    )
    assertEquals(
      value.toJson(spaces = 0, sort = true),
      """{"a":{"c":3,"d":4},"arr":[{"e":5,"f":6}],"b":1}"""
    )
    assertEquals(
      value.toJson(spaces = -1, sort = true),
      """{"a":{"c":3,"d":4},"arr":[{"e":5,"f":6}],"b":1}"""
    )
  }

  test("normalize negative spaces to dense output") {
    assertEquals(
      CaseClass2("c2", CaseClass1("str", 123)).toJson(spaces = -5),
      CaseClass2("c2", CaseClass1("str", 123)).toJson(spaces = 0)
    )
  }

}
