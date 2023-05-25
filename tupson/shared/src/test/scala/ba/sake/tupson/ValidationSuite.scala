package ba.sake.tupson

import ba.sake.validation.*

class ValidationSuite extends munit.FunSuite {

  test("validate case class") {
    val ex1 = intercept[FieldsValidationException] {
      """{ "str": "a", "integer": -50, "list": [{"str": "bsss"}] }""".parseJson[ValidCaseClass2]
    }

    assertEquals(
      ex1.errors,
      Seq(
        FieldValidationError("$.str", "a", "must be > 3"),
        FieldValidationError("$.integer", -50, "must be positive")
      )
    )

    val ex2 = intercept[FieldsValidationException] {
      """{ "str": "a", "integer": -50, "list": [{"str": "b"}] }""".parseJson[ValidCaseClass2]
    }

    assertEquals(
      ex2.errors,
      Seq(
        FieldValidationError("$.list[0].str", "b", "must be > 3"),
        FieldValidationError("$.str", "a", "must be > 3"),
        FieldValidationError("$.integer", -50, "must be positive")
      )
    )
  }
}

case class ValidCaseClass1(str: String) derives JsonRW {
  import ba.sake.validation.*
  validate(
    check(str).is(_.length > 3, "must be > 3").is(_.length < 10, "must be < 10")
  )
}
case class ValidCaseClass2(str: String, integer: Int, list: List[ValidCaseClass1]) derives JsonRW {
  import ba.sake.validation.*
  validate(
    check(str).is(_.length > 3, "must be > 3").is(_.length < 10, "must be < 10"),
    check(integer).is(_ > 0, "must be positive")
  )
}
