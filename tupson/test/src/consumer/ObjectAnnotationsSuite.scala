package consumer

import ba.sake.tupson.{*, given}
import scala.collection.immutable.VectorMap

class ObjectAnnotationsSuite extends munit.FunSuite {
  import ObjectAnnotationsSuite.*

  test("flatten generic nested products directly in declaration and sorted order") {
    val model = Record("event", Details(Location("Sarajevo"), count = 3), active = true)
    val json = """{"name":"event","city name":"Sarajevo","count":3,"active":true}"""
    assertEquals(json.parseJson[Record], model)
    assertEquals(model.toJson(), json)
    assertEquals(model.toJson(sort = true), """{"active":true,"city name":"Sarajevo","count":3,"name":"event"}""")
    assertEquals("""{"active":true,"city name":"Sarajevo","name":"event"}""".parseJson[Record], model)
  }

  test("use the flattened parameter default only when no flattened field is present") {
    val fallback = Defaulted(Details(Location("default"), 10))
    assertEquals("{}".parseJson[Defaulted], fallback)
    assertEquals(
      """{"city name":"Sarajevo"}""".parseJson[Defaulted],
      Defaulted(Details(Location("Sarajevo"), 3))
    )
    val error = intercept[ParsingException] {
      """{"count":4}""".parseJson[Defaulted]
    }
    assertEquals(error.errors, Seq(ParseError("$.city name", "is missing")))
  }

  test("report flattened validation paths and reject duplicate flattened keys") {
    val error = intercept[ParsingException] {
      """{"name":false,"city name":1,"active":"on"}""".parseJson[Record]
    }
    assertEquals(error.errors.map(_.path), Seq("$.name", "$.city name", "$.active"))
    interceptMessage[TupsonException]("Duplicate JSON key at '$.city name'") {
      """{"name":"event","city name":"a","city\u0020name":"b","active":true}""".parseJson[Record]
    }
  }

  test("preserve Patch omission and null handling on flattened fields") {
    val omitted = Update(Changes(Patch.Keep))
    assertEquals(omitted.toJson(), "{}")
    assertEquals("{}".parseJson[Update], omitted)
    val explicitNull = """{"label":null}"""
    assertEquals(explicitNull.parseJson[Update], Update(Changes(Patch.Set(None))))
    assertEquals(Update(Changes(Patch.Set(None))).toJson(), explicitNull)
  }

  test("capture arbitrary unknown values and merge sorted keys across both annotations") {
    val json = """{"a":{"z":null,"a":true},"city name":"Sarajevo","count":3,"id":1,"z":[false,2e10]}"""
    val expected = Envelope(
      1,
      Details(Location("Sarajevo")),
      VectorMap(
        "a" -> Json.Obj(VectorMap("z" -> Json.Null, "a" -> Json.Bool(true))),
        "z" -> Json.Arr(Vector(Json.Bool(false), Json.Num("2e10")))
      )
    )
    val value = json.parseJson[Envelope]
    assertEquals(value, expected)
    assertEquals(value.extra.keysIterator.toList, List("a", "z"))
    assertEquals(value.toJson(), """{"id":1,"city name":"Sarajevo","count":3,"a":{"z":null,"a":true},"z":[false,2e10]}""")
    assertEquals(value.toJson(sort = true), """{"a":{"a":true,"z":null},"city name":"Sarajevo","count":3,"id":1,"z":[false,2e10]}""")
    assertEquals("""{"id":1,"city name":"Sarajevo"}""".parseJson[Envelope].extra, Map.empty[String, Json])
  }

  test("reject duplicate unknown keys and writing extras with reserved keys") {
    interceptMessage[TupsonException]("Duplicate JSON key at '$.extra'") {
      """{"id":1,"city name":"Sarajevo","extra":null,"\u0065xtra":false}""".parseJson[Envelope]
    }
    for key <- Seq("id", "city name", "count"); sort <- Seq(false, true) do
      interceptMessage[TupsonException](s"@other field conflicts with JSON key '$key'") {
        Envelope(1, Details(Location("Sarajevo")), Map(key -> Json.Null)).toJson(sort = sort)
      }
  }

  test("dispatch tagged products with late discriminators without capturing the tag") {
    val json = """{"extra":null,"city name":"Sarajevo","kind":"Created"}"""
    val expected: Event = Created(Location("Sarajevo"), Map("extra" -> Json.Null))
    assertEquals(json.parseJson[Event], expected)
    assertEquals(expected.toJson(sort = true), """{"city name":"Sarajevo","extra":null,"kind":"Created"}""")
    val collision: Event = Created(Location("Sarajevo"), Map("kind" -> Json.Null))
    interceptMessage[TupsonException]("@other field conflicts with JSON key 'kind'")(collision.toJson())
  }

  test("reject ambiguous wire keys at derivation time") {
    val duplicate = compileErrors("""
      case class Nested(id: Int)
      case class Invalid(id: Int, @flatten nested: Nested) derives JsonRW
    """)
    assert(duplicate.contains("Duplicate JSON key 'id'"), duplicate)

    val tag = compileErrors("""
      @discriminator("kind") sealed trait Event derives JsonRW
      case class Nested(kind: Int)
      case class Started(@flatten nested: Nested) extends Event
    """)
    assert(tag.contains("Discriminator 'kind' conflicts"), tag)
  }

  test("reject unsupported annotation targets at derivation time") {
    val repeated = compileErrors("""
      case class Invalid(@other first: Map[String, Json], @other second: Map[String, Json]) derives JsonRW
    """)
    assert(repeated.contains("Only one @other field"), repeated)

    val wrongType = compileErrors("""
      case class Invalid(@other extra: Map[String, Int]) derives JsonRW
    """)
    assert(wrongType.contains("@other requires Map[String, Json]"), wrongType)

    val optional = compileErrors("""
      case class Invalid(@flatten nested: Option[Int]) derives JsonRW
    """)
    assert(optional.contains("@flatten requires a case class"), optional)

    val recursive = compileErrors("""
      case class Invalid(@flatten nested: Invalid) derives JsonRW
    """)
    assert(recursive.contains("Recursive @flatten"), recursive)

    val nestedExtras = compileErrors("""
      case class Nested(@other extra: Map[String, Json])
      case class Invalid(@flatten nested: Nested) derives JsonRW
    """)
    assert(nestedExtras.contains("@other is not supported inside a flattened product"), nestedExtras)
  }
}

object ObjectAnnotationsSuite {
  case class Location[A](`city name`: A)
  case class Details(@flatten location: Location[String], count: Int = 3)
  case class Record(name: String, @flatten details: Details, active: Boolean) derives JsonRW
  case class Defaulted(@flatten details: Details = Details(Location("default"), 10)) derives JsonRW
  case class Changes(label: Patch[Option[String]])
  case class Update(@flatten changes: Changes) derives JsonRW
  case class Envelope(id: Int, @flatten details: Details, @other extra: Map[String, Json] = Map.empty) derives JsonRW

  @discriminator("kind")
  sealed trait Event derives JsonRW
  case class Created(@flatten location: Location[String], @other extra: Map[String, Json]) extends Event
}
