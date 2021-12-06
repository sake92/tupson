package ba.sake.tupson

import JsonWriter.toJson

class JsonWriterSuite extends munit.FunSuite {

  test("write primitives") {
    assertEquals(true.toJson, "true")
    assertEquals(false.toJson, "false")

    // https://github.com/scala-js/scala-js/blob/v1.7.1/test-suite/shared/src/test/scala/org/scalajs/testsuite/javalib/lang/FloatTest.scala#L81-L85
    assertEquals(1.233F.toJson.substring(0, 5), "1.233")

    assertEquals(1.234_567_89D.toJson, "1.23456789")

    assertEquals(1.toJson, "1")

    assertEquals("".toJson, "\"\"")
    assertEquals("abc".toJson, "\"abc\"")
  }

  test("write Long") {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER
    assertEquals(9007199254740991L.toJson, "9007199254740991")
    assertEquals(9007199254740992L.toJson, "\"9007199254740992\"")

    assertEquals(-9007199254740991L.toJson, "-9007199254740991")
    assertEquals(-9007199254740992L.toJson, "\"-9007199254740992\"")
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

  /* case class */
  test("write case class") {
    assertEquals(
      CaseClass1("str", 123).toJson,
      """{"str": "str", "int": 123}"""
    )

    assertEquals(
      CaseClass2("c2", CaseClass1("str", 123)).toJson,
      """{"bla": "c2", "c1": {"str": "str", "int": 123}}"""
    )
  }

  /* sealed trait */
  test("write sealed trait hierarchy") {
    import seal.*
    val s1: Sealed1 = Sealed1Case("str", 123)
    assertEquals(
      s1.toJson,
      """{"@type":"ba.sake.tupson.seal.Sealed1Case", "str": "str", "integer": 123}"""
    )
  }

  /* enum */
  test("write enum hierarchy") {
    import enums.*
    val s1: Enum1 = Enum1.Enum1Case("str", Some(123))
    val s2: Enum1 = Enum1.Enum1Case("str", None)
    assertEquals(
      s1.toJson,
      """{"@type":"ba.sake.tupson.enums.Enum1$.Enum1Case", "str": "str", "integer": 123}"""
    )
    assertEquals(
      s2.toJson,
      """{"@type":"ba.sake.tupson.enums.Enum1$.Enum1Case", "str": "str", "integer": null}"""
    )
  }

}

case class CaseClass1(str: String, @JsonProperty("int") integer: Int)
case class CaseClass2(bla: String, c1: CaseClass1)

package seal {
  sealed trait Sealed1
  case class Sealed1Case(str: String, integer: Int) extends Sealed1
  case class Sealed2Case(str: String) extends Sealed1
}

package enums {
  enum Enum1:
    case Enum1Case(str: String, integer: Option[Int])
    case Enum2Case(str: String)
}
