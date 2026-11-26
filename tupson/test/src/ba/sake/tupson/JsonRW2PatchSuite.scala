package ba.sake.tupson

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
import JsonRW2.{parseJson as parseJson2, toJson as toJson2}
import TupsonReader.DecodeContext

class JsonRW2PatchSuite extends munit.FunSuite {
  import JsonRW2PatchSuite.*
  import Patch.*

  test("distinguish absent, null, and present PATCH fields") {
    assertEquals("{}".parseJson2[UserPatch], UserPatch(Keep, Keep))
    val input = """{"name":"Ada","address":null}"""
    val patch = UserPatch(Set("Ada"), Set(None))
    assertEquals(input.parseJson2[UserPatch], patch)
    assertEquals(patch.toJson2(), input)
  }

  test("omit Keep fields for sorted and unsorted output") {
    val patch = UserPatch(Set("Ada"), Keep)
    assertEquals(patch.toJson2(), """{"name":"Ada"}""")
    assertEquals(patch.toJson2(sort = true), """{"name":"Ada"}""")
    assertEquals(UserPatch(Keep, Keep).toJson2(), "{}")
  }

  test("reject null for nonnullable fields and Keep outside a product field") {
    val error = intercept[ParsingException]("""{"name":null}""".parseJson2[UserPatch])
    assertEquals(error.errors, Seq(ParseError("$.name", "should be String but it is Null", Some("null"))))

    val keep: Patch[String] = Keep
    interceptMessage[TupsonException]("Patch.Keep can only be written as an object field") {
      keep.toJson2()
    }
  }
}

object JsonRW2PatchSuite {
  enum Patch[+A] {
    case Keep
    case Set(value: A)
  }

  object Patch {
    given [A](using element: JsonRW2[A]): JsonRW2[Patch[A]] with {
      override private[tupson] def default: Option[Patch[A]] = Some(Keep)
      override private[tupson] def shouldWriteField(value: Patch[A]): Boolean = value != Keep

      private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Patch[A] = {
        val before = ctx.errorCount
        val value = element.decode(in, ctx)
        if ctx.errorCount == before then Set(value) else Keep
      }

      private[tupson] def encode(value: Patch[A], out: JsonWriter, sort: Boolean): Unit =
        value match {
          case Set(item) => element.encode(item, out, sort)
          case Keep      => throw TupsonException("Patch.Keep can only be written as an object field")
        }
    }
  }

  case class UserPatch(name: Patch[String], address: Patch[Option[String]]) derives JsonRW2
}
