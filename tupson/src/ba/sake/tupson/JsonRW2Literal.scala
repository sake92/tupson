package ba.sake.tupson

import scala.compiletime.summonFrom
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
import TupsonReader.DecodeContext

private[tupson] object JsonRW2Literal {
  inline def derived[A <: String | Char | Boolean | Int | Long | Float | Double](using valueOf: ValueOf[A]): JsonRW2[A] =
    summonFrom {
      case ev: (A <:< String)  => literal(valueOf.value, JsonRW2[String])(using ev)
      case ev: (A <:< Char)    => literalChar(valueOf.value)(using ev)
      case ev: (A <:< Boolean) => literal(valueOf.value, JsonRW2[Boolean])(using ev)
      case ev: (A <:< Int)     => literal(valueOf.value, JsonRW2[Int])(using ev)
      case ev: (A <:< Long)    => literal(valueOf.value, JsonRW2[Long])(using ev)
      case ev: (A <:< Float)   => literal(valueOf.value, JsonRW2[Float])(using ev)
      case ev: (A <:< Double)  => literal(valueOf.value, JsonRW2[Double])(using ev)
    }

  private def literal[A, B](expected: A, codec: JsonRW2[B])(using ev: A <:< B): JsonRW2[A] =
    new JsonRW2[A] {
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

  private def literalChar[A](expected: A)(using ev: A <:< Char): JsonRW2[A] =
    new JsonRW2[A] {
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
