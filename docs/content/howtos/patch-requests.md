---
title: Handling PATCH requests
description: Model partial updates in Tupson
---

# {{ page.title }}

For an existing user with a required name and nullable address, a frontend expects these PATCH request bodies to mean:

```json
{}                    // keep every field unchanged
{ "address": null }  // clear the address
{ "name": "Ada" }   // replace the name
```

When a frontend sends a PATCH request without a field, it expects the existing value to remain unchanged. Model whether a field was sent separately from whether its value is nullable.

That distinction is awkward in many Scala JSON libraries. A field codec is often only asked to produce a JSON value, so it cannot say “omit my enclosing key” when the field should be left unchanged; doing so requires a custom product encoder.

Use a two-state `Patch[T]` with Tupson. Its `shouldWriteField` hook lets the codec omit a `Keep` field when serializing an object. Use `Patch[String]` for required fields and `Patch[Option[String]]` for nullable fields, so JSON `null` becomes `Set(None)`.

The following runnable example defines the codec, parses a request body, and writes a patch. A missing key becomes `Keep`. A present value becomes `Set`, including `Set(None)` for an explicit `null` in a nullable field.

<!-- example:start examples/src/main/scala/patch.scala -->
```scala
import ba.sake.tupson.{*, given}
import org.typelevel.jawn.ast.*

/** A field update for a JSON PATCH-style API.
  *
  * A missing key is [[Patch.Keep]] and a present value is [[Patch.Set]]. Use `Patch[Option[T]]` for nullable fields.
  */
enum Patch[+T]:
  case Set(value: T)
  case Keep

object Patch:
  given [T](using valueRW: JsonRW[T]): JsonRW[Patch[T]] with
    override def write(value: Patch[T]): JValue = value match
      case Set(value) => valueRW.write(value)
      case Keep =>
        throw TupsonException("Patch.Keep can only be written as an object field")

    override def shouldWriteField(value: Patch[T]): Boolean = value != Keep

    override def parse(path: String, jValue: JValue): Patch[T] =
      Set(valueRW.parse(path, jValue))

    override def default: Option[Patch[T]] = Some(Keep)

case class UserPatch(name: Patch[String], address: Patch[Option[String]]) derives JsonRW

@main def patchExample: Unit =
  val userPatch = """{ "name": "Ada", "address": null }""".parseJson[UserPatch]
  println(userPatch) // UserPatch(Set(Ada), Set(None))

  val nameOnly = UserPatch(Patch.Set("Ada"), Patch.Keep)
  println(nameOnly.toJson(spaces = 0)) // {"name":"Ada"}

  val noChanges = UserPatch(Patch.Keep, Patch.Keep)
  println(noChanges.toJson(spaces = 0)) // {}
```
<!-- example:end -->

Use `Patch` only as an object field. A top-level `Patch.Keep` has no JSON representation and fails to write.
