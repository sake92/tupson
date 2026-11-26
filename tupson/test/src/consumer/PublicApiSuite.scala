package consumer

import ba.sake.tupson.{*, given}

class PublicApiSuite extends munit.FunSuite {
  import PublicApiSuite.*

  test("derive and use a codec from a consumer package") {
    val value = """{"name":"backup"}""".parseJson[Job]
    assertEquals(value, Job("backup", 3))
    assertEquals(value.toJson(), """{"name":"backup","retries":3}""")
  }

  test("resolve list and sequence codecs through a contravariant writer") {
    def writeBody[A: BodyWriter](value: A): String = summon[BodyWriter[A]].write(value)

    val jobs = List(Job("backup", 3))
    val sequence: Seq[Job] = Vector(Job("backup", 3))
    val expected = """[{"name":"backup","retries":3}]"""
    assertEquals(writeBody(jobs), expected)
    assertEquals(writeBody(sequence), expected)
  }

  test("keep raw codec construction internal") {
    val errors = compileErrors("new ba.sake.tupson.JsonRW[Int]() {}")
    assert(errors.contains("constructor JsonRW cannot be accessed"), errors)
  }
}

object PublicApiSuite {
  case class Job(name: String, retries: Int = 3) derives JsonRW
}

private trait BodyWriter[-A] {
  def write(value: A): String
}

private object BodyWriter {
  given [A: JsonRW]: BodyWriter[A] with {
    def write(value: A): String = value.toJson()
  }
}
