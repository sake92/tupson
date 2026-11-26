package ba.sake.tupson


class PatchSuite extends munit.FunSuite {
  import PatchSuite.*
  import Patch.*

  test("distinguish absent, null, and present PATCH fields") {
    assertEquals("{}".parseJson[UserPatch], UserPatch(Keep, Keep, Keep))
    val input = """{"name":"Ada","address":null}"""
    val patch = UserPatch(Set("Ada"), Set(None), Keep)
    assertEquals(input.parseJson[UserPatch], patch)
    assertEquals(patch.toJson(), input)
    assertEquals("""{"tags":[]}""".parseJson[UserPatch], UserPatch(Keep, Keep, Set(Seq.empty)))
  }

  test("omit Keep fields for sorted and unsorted output") {
    val patch = UserPatch(Set("Ada"), Keep, Keep)
    assertEquals(patch.toJson(), """{"name":"Ada"}""")
    assertEquals(patch.toJson(sort = true), """{"name":"Ada"}""")
    assertEquals(UserPatch(Keep, Keep, Keep).toJson(), "{}")
  }

  test("reject null for nonnullable fields and Keep outside a product field") {
    val error = intercept[ParsingException]("""{"name":null}""".parseJson[UserPatch])
    assertEquals(error.errors, Seq(ParseError("$.name", "should be String but it is Null", Some("null"))))

    val set: Patch[Int] = Set(42)
    assertEquals("42".parseJson[Patch[Int]], set)
    assertEquals(set.toJson(), "42")

    val keep: Patch[String] = Keep
    interceptMessage[TupsonException]("Patch.Keep can only be written as an object field") {
      keep.toJson()
    }
  }
}

object PatchSuite {
  case class UserPatch(name: Patch[String], address: Patch[Option[String]], tags: Patch[Seq[String]]) derives JsonRW
}
