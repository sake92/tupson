package ba.sake.tupson

import scala.deriving.Mirror
import scala.quoted.*
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
import TupsonReader.DecodeContext

private[tupson] object JsonRW2Sum {
  inline def derived[A](inline mirror: Mirror.SumOf[A]): JsonRW2[A] = ${ derive[A]('mirror) }

  private def derive[A: Type](mirror: Expr[Mirror.SumOf[A]])(using Quotes): Expr[JsonRW2[A]] =
    mirror match {
      case '{
            $sum: Mirror.SumOf[A] {
              type MirroredElemTypes = elements
              type MirroredElemLabels = labels
            }
          } => deriveEnum[A, elements, labels](sum)
      case _ => quotes.reflect.report.errorAndAbort(s"Cannot resolve sum cases for ${Type.show[A]}")
    }

  private def deriveEnum[A: Type, Elements: Type, Labels: Type](sum: Expr[Mirror.SumOf[A]])(using
      Quotes
  ): Expr[JsonRW2[A]] = {
    import quotes.reflect.*

    val model = TypeRepr.of[A].dealias.typeSymbol
    if !model.flags.is(Flags.Enum) || model.companionClass.methodMember("values").isEmpty then
      report.errorAndAbort(s"JsonRW2 sum derivation currently supports singleton-only enums: ${Type.show[A]}")

    def entries[Cases: Type, Names: Type]: List[Expr[(String, A)]] =
      (Type.of[Cases], Type.of[Names]) match {
        case ('[EmptyTuple], '[EmptyTuple]) => Nil
        case ('[element *: tail], '[name *: names]) =>
          val label = Type.valueOfConstant[name] match {
            case Some(value: String) => Expr(value)
            case _ => report.errorAndAbort(s"Cannot resolve enum label ${Type.show[name]}")
          }
          val singleton = Expr.summon[ValueOf[element]].getOrElse {
            report.errorAndAbort(s"Cannot resolve enum case ${Type.show[element]}")
          }
          '{ ($label, $singleton.value.asInstanceOf[A]) } :: entries[tail, names]
        case _ => report.errorAndAbort(s"Cannot resolve enum cases for ${Type.show[A]}")
      }

    val cases = Expr.ofList(entries[Elements, Labels])
    '{ new EnumCodec[A]($sum, $cases) }
  }

  private[tupson] final class EnumCodec[A](mirror: Mirror.SumOf[A], cases: List[(String, A)]) extends JsonRW2[A] {
    private val values = cases.toMap
    private val names = cases.iterator.map(_._1).toArray
    private val possibleValues = names.iterator.map(name => s"'$name'").mkString(", ")

    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): A =
      TupsonReader.readToken(in, ctx, "String", null.asInstanceOf[A])(_ == '"') {
        val name = in.readString(null)
        values.getOrElse(name, throw TupsonException(s"Enum value not found: '$name'. Possible values: $possibleValues"))
      }

    private[tupson] def encode(value: A, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(names(mirror.ordinal(value)))
  }
}
