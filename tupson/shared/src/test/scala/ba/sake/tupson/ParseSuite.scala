package ba.sake.tupson

class ParseSuite extends munit.FunSuite {

  test("parse primitives") {
    assertEquals("true".parseJson[Boolean], true)
    assertEquals("false".parseJson[Boolean], false)
    intercept[TupsonException] {
      """5""".parseJson[Boolean]
    }

    // https://github.com/scala-js/scala-js/blob/v1.7.1/test-suite/shared/src/test/scala/org/scalajs/testsuite/javalib/lang/FloatTest.scala#L81-L85
    assertEquals("1.233".parseJson[Float], 1.233f)
    intercept[TupsonException] {
      """5""".parseJson[Float]
    }

    assertEquals("1.23456789".parseJson[Double], 1.234_567_89d)

    assertEquals("1".parseJson[Int], 1)
    intercept[TupsonException] {
      """5.0""".parseJson[Int]
    }

    assertEquals("1".parseJson[Long], 1L)

    assertEquals("\"\"".parseJson[String], "")
    assertEquals("\"abc\"".parseJson[String], "abc")

    assertEquals("\"a\"".parseJson[Char], 'a')
  }

  test("parse Long") {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER
    // TODO see if JS likes this..
    assertEquals("9007199254740991".parseJson[Long], 9007199254740991L)
    assertEquals("9007199254740992".parseJson[Long], 9007199254740992L)

    assertEquals("-9007199254740991".parseJson[Long], -9007199254740991L)
    assertEquals("-9007199254740992".parseJson[Long], -9007199254740992L)
  }

  test("parse Seq") {
    assertEquals("[1,2,3]".parseJson[Seq[Int]], Seq(1, 2, 3))
    assertEquals("[1,2,3]".parseJson[List[Int]], List(1, 2, 3))
    assertEquals("[1,2,3]".parseJson[Array[Int]].toSeq, Array(1, 2, 3).toSeq)
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

    intercept[MissingKeysException] {
      // missing "integer" key
      """{"str":"str"}""".parseJson[CaseClass1]
    }
    intercept[TupsonException] {
      // has to be an object
      """5""".parseJson[CaseClass1]
    }
  }

  /* sealed trait */
  test("parse sealed trait hierarchy") {
    import seal.*
    assertEquals(
      """{"str":"str","@type":"ba.sake.tupson.seal.Sealed1Case","integer":123}"""
        .parseJson[SealedBase],
      Sealed1Case("str", 123)
    )
  }

  /* enum */
  test("parse enum hierarchy") {
    import enums.*
    assertEquals(
      """{"str":"str","@type":"ba.sake.tupson.enums.Enum1.Enum1Case","integer":123}"""
        .parseJson[Enum1],
      Enum1.Enum1Case("str", Some(123))
    )
    assertEquals(
      """{"str":"str","@type":"ba.sake.tupson.enums.Enum1.Enum1Case","integer":null}"""
        .parseJson[Enum1],
      Enum1.Enum1Case("str", None)
    )
  }

  /* recursive data type */
  test("parse recursive data type") {
    import rec.*
    assertEquals(
      """{"children":[{"children":[]}]}""".parseJson[Node],
      Node(List(Node(List.empty)))
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

}