package ba.sake.tupson

import scala.compiletime.summonFrom
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
import TupsonReader.DecodeContext

private[tupson] object JsonRWLiteral {
  inline def derived[A <: String | Char | Boolean | Int | Long | Float | Double](using valueOf: ValueOf[A]): JsonRW[A] =
    summonFrom {
      case ev: (A <:< String)  => literal(valueOf.value, JsonRW[String])(using ev)
      case ev: (A <:< Char)    => literalChar(valueOf.value)(using ev)
      case ev: (A <:< Boolean) => literal(valueOf.value, JsonRW[Boolean])(using ev)
      case ev: (A <:< Int)     => literal(valueOf.value, JsonRW[Int])(using ev)
      case ev: (A <:< Long)    => literal(valueOf.value, JsonRW[Long])(using ev)
      case ev: (A <:< Float)   => literal(valueOf.value, JsonRW[Float])(using ev)
      case ev: (A <:< Double)  => literal(valueOf.value, JsonRW[Double])(using ev)
    }

  private def literal[A, B](expected: A, codec: JsonRW[B])(using ev: A <:< B): JsonRW[A] =
    new JsonRW[A] {
      private val message = s"should be literal value '$expected'"

      private[tupson] def decode(in: JsonReader, ctx: DecodeContext): A = {
        val before = ctx.errorCount
        val value = codec.decode(in, ctx)
        if ctx.errorCount == before && value != ev(expected) then ctx.fail(message, Some(value))
        expected
      }

      private[tupson] def encode(value: A, out: JsonWriter, sort: Boolean): Unit =
        codec.encode(ev(value), out, sort)
    }

  private def literalChar[A](expected: A)(using ev: A <:< Char): JsonRW[A] =
    new JsonRW[A] {
      private val expectedChar = ev(expected)
      private val message = s"should be literal value '$expected'"

      private[tupson] def decode(in: JsonReader, ctx: DecodeContext): A =
        TupsonReader.readToken(in, ctx, "Char", expected)(_ == '"') {
          val text = in.readString(null)
          if text.length != 1 then ctx.fail(message, Some(text))
          else if text.charAt(0) != expectedChar then ctx.fail(message, Some(text.charAt(0)))
          expected
        }

      private[tupson] def encode(value: A, out: JsonWriter, sort: Boolean): Unit =
        out.writeVal(ev(value))
    }
}
