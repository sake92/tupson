package ba.sake.tupson

import ProductFixtures.*

class FormattingSuite extends munit.FunSuite {

  test("write compact by default and allow pretty sorted output") {
    assertEquals(
      CaseClass1("str", 123).toJson(spaces = 2, sort = true),
      """{
        |  "integer": 123,
        |  "str": "str"
        |}""".stripMargin
    )
    assertEquals(
      CaseClass1("str", 123).toJson(),
      """{"str":"str","integer":123}"""
    )
  }

  test("write custom spaced output") {
    assertEquals(
      Seq(CaseClass1("str", 123), CaseClass1("x", 456)).toJson(spaces = 4, sort = true),
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
      CaseClass1("str", 123).toJson(spaces = 2, sort = false),
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

  test("normalize negative spaces to dense output") {
    assertEquals(
      CaseClass2("c2", CaseClass1("str", 123)).toJson(spaces = -5),
      CaseClass2("c2", CaseClass1("str", 123)).toJson(spaces = 0)
    )
  }
}
