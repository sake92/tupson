package ba.sake.tupson

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
    assertEquals(Seq(1, 2, 3).toJson, "[1,2,3]")
    assertEquals(List(1, 2, 3).toJson, "[1,2,3]")
    assertEquals(Array(1, 2, 3).toJson, "[1,2,3]")
  }

  test("write Option") {
    assertEquals(Option(123).toJson, "123")
    assertEquals(Option.empty[Int].toJson, "null")
  }

  test("write Map") {
    assertEquals(Map("a" -> "abc").toJson, """{"a":"abc"}""")
    assertEquals(
      Map("string" -> "abc").toJson,
      """{"string":"abc"}"""
    )
  }

  /* case class */
  test("write case class") {
    assertEquals(
      CaseClass1("str", 123).toJson,
      """{"str":"str","integer":123}"""
    )

    assertEquals(
      CaseClass2("c2", CaseClass1("str", 123)).toJson,
      """{"bla":"c2","c1":{"str":"str","integer":123}}"""
    )
  }

  /* sealed trait */
  test("write sealed trait hierarchy") {
    import seal.*
    val s1: SealedBase = Sealed1Case("str", 123)
    assertEquals(
      s1.toJson,
      """{"str":"str","@type":"Sealed1Case","integer":123}"""
    )
  }

  /* enum */
  test("write enum hierarchy") {
    import enums.*
    val s1: Enum1 = Enum1.Enum1Case("str", Some(123))
    val s2: Enum1 = Enum1.Enum1Case("str", None)
    val s3: Enum1 = Enum1.`eNum CaseD`
    assertEquals(
      s1.toJson,
      """{"str":"str","@type":"Enum1Case","integer":123}"""
    )
    assertEquals(
      s2.toJson,
      """{"str":"str","@type":"Enum1Case","integer":null}"""
    )
    assertEquals(
      s3.toJson,
      """{"@type":"eNum CaseD"}"""
    )
  }

  test("write nested hierarchy like flat type name") {
    import enums.*

    val burried1 = inner.burried.Inside.Abc
    assertEquals(
      burried1.toJson,
      """{"@type":"Abc"}"""
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
    val n1 = Node(List(Node(List.empty)))
    assertEquals(
      n1.toJson,
      """{"children":[{"children":[]}]}"""
    )
  }

  /* weird key names */
  test("write with weird key name") {
    import weird_named.*
    val r1 = WeirdNamed(1)
    assertEquals(
      r1.toJson,
      """{"weird named key":1}"""
    )
  }

}
