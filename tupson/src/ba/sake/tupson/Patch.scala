package ba.sake.tupson

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
import TupsonReader.DecodeContext

/** A field update. Use Patch[Option[A]] for nullable fields. */
enum Patch[+A] {
  case Keep
  case Set(value: A)
}

object Patch {
  given [A](using element: JsonRW[A]): JsonRW[Patch[A]] with {
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
