package ba.sake.tupson

import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.immutable.VectorMap
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
import TupsonReader.{DecodeContext, isNumber, readArray, readObjectMembers}

sealed trait Json

object Json {
  private val numberPattern = "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?".r

  case object Null extends Json
  final case class Bool(value: Boolean) extends Json
  final case class Num(raw: String) extends Json {
    require(numberPattern.pattern.matcher(raw).matches(), "Invalid JSON number")
  }
  final case class Str(value: String) extends Json
  final case class Arr(values: Vector[Json]) extends Json
  final case class Obj(fields: VectorMap[String, Json]) extends Json

  given JsonRW[Json] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Json = {
      val token = in.nextToken()
      if token == 'n' then in.readNullOrError(Null, "expected null")
      else {
        in.rollbackToken()
        token match {
          case 't' | 'f' => Bool(in.readBoolean())
          case '"'       => Str(in.readString(null))
          case '['       => Arr(readArray(in, ctx, this, "Array", Vector.empty[Json])(Vector.newBuilder[Json]))
          case '{' =>
            in.nextToken()
            val fields = VectorMap.newBuilder[String, Json]
            val keys = scala.collection.mutable.HashSet.empty[String]
            readObjectMembers(in)(in.readKeyAsString()) { key =>
              if !keys.add(key) then ctx.duplicateKey(key)
              val value = ctx.withField(key)(decode(in, ctx))
              fields.addOne((key, value))
            }
            Obj(fields.result())
          case number if isNumber(number) =>
            val raw = new String(in.readRawValAsBytes(), UTF_8)
            try Num(raw)
            catch { case _: IllegalArgumentException => in.decodeError("expected JSON number") }
          case _ => in.decodeError("expected JSON value")
        }
      }
    }

    private[tupson] def encode(value: Json, out: JsonWriter, sort: Boolean): Unit =
      value match {
        case Null       => out.writeNull()
        case Bool(flag) => out.writeVal(flag)
        case Num(raw)   => out.writeRawVal(raw.getBytes(UTF_8))
        case Str(text)  => out.writeVal(text)
        case Arr(items) => TupsonWriter.writeArray(items.iterator, out, sort, this)
        case Obj(fields) =>
          val entries = if sort then fields.toVector.sortBy(_._1).iterator else fields.iterator
          out.writeObjectStart()
          while entries.hasNext do {
            val (key, field) = entries.next()
            out.writeKey(key)
            encode(field, out, sort)
          }
          out.writeObjectEnd()
      }
  }
}
