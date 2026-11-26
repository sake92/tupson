package ba.sake.tupson

import scala.NamedTuple.{AnyNamedTuple, DropNames, Names}
import scala.compiletime.{constValueTuple, summonAll}
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
import TupsonReader.DecodeContext

private[tupson] object JsonRWNamedTuple {
  inline def derived[A <: AnyNamedTuple]: JsonRW[A] = {
    val names = constValueTuple[Names[A]].productIterator.map(_.asInstanceOf[String]).toArray
    val codecs = summonAll[Tuple.Map[DropNames[A], JsonRW]].productIterator
      .map(_.asInstanceOf[JsonRW[Any]])
      .toArray
    new NamedTupleCodec[A](names, codecs)
  }

  private[tupson] final class NamedTupleCodec[A <: AnyNamedTuple](
      names: Array[String],
      codecs: Array[JsonRW[Any]]
  ) extends JsonRW[A] {
    private val fieldOrder = names.indices.toArray
    private val sortedFieldOrder = fieldOrder.sortBy(names(_))

    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): A =
      TupsonReader.readToken(in, ctx, "JObject", null.asInstanceOf[A])(_ == '{') {
        in.nextToken()
        val values = new Array[Any](names.length)
        val seen = new Array[Boolean](names.length)
        val before = ctx.errorCount
        TupsonReader.readObjectFields(in) { length =>
          var index = 0
          while index < names.length && !in.isCharBufEqualsTo(length, names(index)) do
            index = index + 1
          if index == names.length then TupsonReader.skipValue(in)
          else {
            val name = names(index)
            if seen(index) then ctx.duplicateKey(name)
            seen(index) = true
            if ctx.errorLimitReached then TupsonReader.skipValue(in)
            else values(index) = ctx.withField(name)(codecs(index).decode(in, ctx))
          }
        }
        var index = 0
        while index < names.length && !ctx.errorLimitReached do {
          if !seen(index) then ctx.withField(names(index))(ctx.fail("is missing"))
          index = index + 1
        }
        if ctx.errorCount == before then Tuple.fromArray(values).asInstanceOf[A]
        else null.asInstanceOf[A]
      }

    private[tupson] def encode(value: A, out: JsonWriter, sort: Boolean): Unit = {
      val tuple = value.asInstanceOf[Tuple]
      val order = if sort then sortedFieldOrder else fieldOrder
      out.writeObjectStart()
      var position = 0
      while position < order.length do {
        val index = order(position)
        out.writeKey(names(index))
        codecs(index).encode(tuple.productElement(index), out, sort)
        position = position + 1
      }
      out.writeObjectEnd()
    }
  }
}
