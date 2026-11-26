package ba.sake.tupson

import scala.quoted.*
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonReaderException, JsonWriter}
import TupsonReader.DecodeContext

private[tupson] object JsonRWUnion {
  inline def derived[A]: JsonRW[A] = ${ derive[A] }

  private def derive[A: Type](using Quotes): Expr[JsonRW[A]] = {
    import quotes.reflect.*

    TypeRepr.of[A].dealias match {
      case OrType(left, right) =>
        (left.asType, right.asType) match {
          case ('[l], '[r]) =>
            val leftCodec = Expr.summon[JsonRW[l]].getOrElse {
              report.errorAndAbort(s"No JsonRW[${Type.show[l]}] for union ${Type.show[A]}")
            }
            val rightCodec = Expr.summon[JsonRW[r]].getOrElse {
              report.errorAndAbort(s"No JsonRW[${Type.show[r]}] for union ${Type.show[A]}")
            }
            '{
              new UnionCodec[A](
                $leftCodec.asInstanceOf[JsonRW[A]],
                $rightCodec.asInstanceOf[JsonRW[A]],
                value => value.isInstanceOf[l]
              )
            }
          case _ => report.errorAndAbort(s"Cannot resolve union alternatives for ${Type.show[A]}")
        }
      case _ => report.errorAndAbort(s"Cannot derive a JsonRW union codec for ${Type.show[A]}")
    }
  }

  private[tupson] final class UnionCodec[A](
      left: JsonRW[A],
      right: JsonRW[A],
      isLeft: A => Boolean
  ) extends JsonRW[A] {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): A = {
      // Each attempt gets its own reader because tagged sums can use a reader mark.
      val json = in.readRawValAsBytes()
      val before = ctx.errorCount
      try {
        val value = TupsonReader.replay(json, left, ctx)
        if ctx.errorCount == before then return value
      } catch {
        case duplicate: DuplicateKeyException => throw duplicate
        case _: TupsonException | _: JsonReaderException => ()
      }

      ctx.discardErrorsAfter(before)
      TupsonReader.replay(json, right, ctx)
    }

    private[tupson] def encode(value: A, out: JsonWriter, sort: Boolean): Unit = {
      val codec = if isLeft(value) then left else right
      codec.encode(value, out, sort)
    }
  }
}
