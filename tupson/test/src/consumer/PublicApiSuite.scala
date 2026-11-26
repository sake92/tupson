package consumer

import ba.sake.tupson.{*, given}

class PublicApiSuite extends munit.FunSuite {
  import PublicApiSuite.*

  test("derive and use a codec from a consumer package") {
    val value = """{"name":"backup"}""".parseJson[Job]
    assertEquals(value, Job("backup", 3))
    assertEquals(value.toJson(), """{"name":"backup","retries":3}""")
  }

  test("keep raw codec construction internal") {
    val errors = compileErrors("new ba.sake.tupson.JsonRW[Int]() {}")
    assert(errors.contains("constructor JsonRW cannot be accessed"), errors)
  }
}

object PublicApiSuite {
  case class Job(name: String, retries: Int = 3) derives JsonRW
}
