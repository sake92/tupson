---
title: Handling PATCH requests
description: Model partial updates in Tupson
---

# {{ page.title }}

Use a three-state `Patch[T]` when an API field may be set, cleared, or left unchanged.

This is awkward in many Scala JSON libraries: a field codec is often only asked to produce a JSON value, so it cannot say “omit my enclosing key” without a custom product encoder. Tupson lets a codec opt out of writing an object field with `shouldWriteField`.

Define the patch type and its codec:

```scala
import ba.sake.tupson.{*, given}
import org.typelevel.jawn.ast.*

enum Patch[+T]:
  case Set(value: T)
  case Clear
  case Keep

object Patch:
  given [T](using valueRW: JsonRW[T]): JsonRW[Patch[T]] with
    override def write(value: Patch[T]): JValue = value match
      case Set(value) => valueRW.write(value)
      case Clear      => JNull
      case Keep =>
        throw TupsonException("Patch.Keep can only be written as an object field")

    override def shouldWriteField(value: Patch[T]): Boolean = value != Keep

    override def parse(path: String, jValue: JValue): Patch[T] = jValue match
      case JNull => Clear
      case other => Set(valueRW.parse(path, other))

    override def default: Option[Patch[T]] = Some(Keep)

case class UserPatch(name: Patch[String], address: Patch[String]) derives JsonRW
```

Parse a request body. A missing key becomes `Keep`, `null` becomes `Clear`, and any other value becomes `Set`:

```scala
val patch = """{ "name": "Ada", "address": null }""".parseJson[UserPatch]
// UserPatch(Patch.Set("Ada"), Patch.Clear)
```

When writing a `UserPatch`, `Keep` fields are omitted:

```scala
UserPatch(Patch.Set("Ada"), Patch.Keep).toJson(spaces = 0)
// {"name":"Ada"}
```

Use `Patch` only as an object field. A top-level `Patch.Keep` has no JSON representation and fails to write.
