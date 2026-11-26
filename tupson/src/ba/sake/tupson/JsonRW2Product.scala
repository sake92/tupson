package ba.sake.tupson

import scala.quoted.*
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}

private[tupson] object JsonRW2Product {
  def derive[A: Type](using Quotes): Expr[JsonRW2[A]] = {
    import quotes.reflect.*

    val model = TypeRepr.of[A].dealias
    val modelSymbol = model.typeSymbol
    val constructor = modelSymbol.primaryConstructor
    val parameters = constructor.paramSymss.filterNot(_.exists(_.isType))
    val accessors = modelSymbol.caseFields
    if !modelSymbol.flags.is(Flags.Case) || parameters.map(_.map(_.name)) != List(accessors.map(_.name)) then
      report.errorAndAbort(s"JsonRW2 requires a case class with one parameter list: ${Type.show[A]}")

    case class Field(accessor: Symbol, tpe: TypeRepr, codec: Term, default: Option[Term]) {
      val name: String = accessor.name
    }
    case class Slot(field: Field, value: Term, seen: Term)

    val typeArguments = model match {
      case AppliedType(_, arguments) => arguments.map(Inferred(_))
      case _                        => Nil
    }

    def applyTypeArguments(method: Term): Term = method.tpe.widen match {
      case _: PolyType => TypeApply(method, typeArguments)
      case _           => method
    }

    val fields = accessors.zipWithIndex.map { (accessor, index) =>
      val tpe = model.memberType(accessor).widen
      tpe.asType match {
        case '[field] =>
          val codec = Expr.summon[JsonRW2[field]].getOrElse {
            report.errorAndAbort(
              s"No JsonRW2[${Type.show[field]}] for ${Type.show[A]}.${accessor.name}"
            )
          }
          val default = if parameters.head(index).flags.is(Flags.HasDefault) then {
            val name = s"$$lessinit$$greater$$default$$${index + 1}"
            val getter = modelSymbol.companionClass.declaredMethod(name).headOption.getOrElse {
              report.errorAndAbort(s"Cannot find constructor default for ${Type.show[A]}.${accessor.name}")
            }
            val method = applyTypeArguments(Select(Ref(modelSymbol.companionModule), getter))
            val value = method.tpe.widen match {
              case _: MethodType => Apply(method, Nil)
              case _             => method
            }
            Some(value)
          } else None
          Field(accessor, tpe, codec.asTerm, default)
      }
    }

    def cacheCodecs(remaining: List[Field], cached: List[Field])(using Quotes): Expr[JsonRW2[A]] =
      remaining match {
        case Nil => makeCodec(cached.reverse)
        case field :: tail =>
          field.tpe.asType match {
            case '[f] =>
              val codec = field.codec.asExprOf[JsonRW2[f]]
              '{
                // A field codec may refer back to this product's given during initialization.
                lazy val fieldCodec = $codec
                ${ cacheCodecs(tail, field.copy(codec = 'fieldCodec.asTerm) :: cached) }
              }
          }
      }

    def makeCodec(fields: List[Field])(using Quotes): Expr[JsonRW2[A]] = {
      val sortedFields = fields.sortBy(_.name)
      '{
        new ProductCodec[A](
          (in, ctx) => TupsonReader.readObject[A](in, ctx) {
            ${ decodeFields(fields, Nil, 'in, 'ctx) }
          },
          (value, out, sort) => {
            out.writeObjectStart()
            if sort then ${ writeFields(sortedFields, 'value, 'out, 'sort) }
            else ${ writeFields(fields, 'value, 'out, 'sort) }
            out.writeObjectEnd()
          }
        )
      }
    }

    def decodeFields(
        remaining: List[Field],
        slots: List[Slot],
        in: Expr[JsonReader],
        ctx: Expr[TupsonReader.DecodeContext]
    )(using Quotes): Expr[A] = remaining match {
      case Nil => readProduct(slots.reverse, in, ctx)
      case field :: tail =>
        field.tpe.asType match {
          case '[f] =>
            '{
              var value: f = null.asInstanceOf[f]
              var seen = false
              ${ decodeFields(tail, Slot(field, 'value.asTerm, 'seen.asTerm) :: slots, in, ctx) }
            }
        }
    }

    def readProduct(slots: List[Slot], in: Expr[JsonReader], ctx: Expr[TupsonReader.DecodeContext])(using Quotes): Expr[A] = {
      val construct = Apply(applyTypeArguments(Select(New(TypeTree.of[A]), constructor)), slots.map(_.value))
      val defaults = Expr.block(slots.map(fillMissing(_, ctx)), '{ () })
      '{
        val before = $ctx.errorCount
        TupsonReader.readObjectFields($in) { length =>
          if $ctx.errorLimitReached then TupsonReader.skipValue($in)
          else ${ readField(slots, in, ctx, 'length) }
        }
        $defaults
        if $ctx.errorCount == before then ${ construct.asExprOf[A] }
        else null.asInstanceOf[A]
      }
    }

    def readField(
        slots: List[Slot],
        in: Expr[JsonReader],
        ctx: Expr[TupsonReader.DecodeContext],
        length: Expr[Int]
    )(using Quotes): Expr[Unit] = slots match {
      case Nil => '{ TupsonReader.skipValue($in) }
      case slot :: tail =>
        slot.field.tpe.asType match {
          case '[f] =>
            val key = Expr(slot.field.name)
            val codec = slot.field.codec.asExprOf[JsonRW2[f]]
            val read = '{ $ctx.withField($key)($codec.decode($in, $ctx)) }
            val assign = Assign(slot.value, read.asTerm).asExprOf[Unit]
            val markSeen = Assign(slot.seen, Expr(true).asTerm).asExprOf[Unit]
            '{
              if $in.isCharBufEqualsTo($length, $key) then {
                $assign
                $markSeen
              } else ${ readField(tail, in, ctx, length) }
            }
        }
    }

    def fillMissing(slot: Slot, ctx: Expr[TupsonReader.DecodeContext])(using Quotes): Expr[Unit] =
      slot.field.tpe.asType match {
        case '[f] =>
          val seen = slot.seen.asExprOf[Boolean]
          val missing = slot.field.default match {
            case Some(value) => Assign(slot.value, value).asExprOf[Unit]
            case None =>
              val codec = slot.field.codec.asExprOf[JsonRW2[f]]
              val key = Expr(slot.field.name)
              '{
                $codec.default match {
                  case Some(value) => ${ Assign(slot.value, 'value.asTerm).asExprOf[Unit] }
                  case None        => $ctx.withField($key)($ctx.fail("is missing"))
                }
              }
          }
          '{ if !$seen && !$ctx.errorLimitReached then $missing }
      }

    def writeFields(fields: List[Field], value: Expr[A], out: Expr[JsonWriter], sort: Expr[Boolean])(using Quotes): Expr[Unit] = {
      val writes = fields.map { field =>
        field.tpe.asType match {
          case '[f] =>
            val key = Expr(field.name)
            val codec = field.codec.asExprOf[JsonRW2[f]]
            val member = Select(value.asTerm, field.accessor).asExprOf[f]
            '{
              $out.writeKey($key)
              $codec.encode($member, $out, $sort)
            }
        }
      }
      Expr.block(writes, '{ () })
    }

    cacheCodecs(fields, Nil)
  }

  private[tupson] final class ProductCodec[A](
      read: (JsonReader, TupsonReader.DecodeContext) => A,
      write: (A, JsonWriter, Boolean) => Unit
  ) extends JsonRW2[A] {
    private[tupson] def decode(in: JsonReader, ctx: TupsonReader.DecodeContext): A = read(in, ctx)
    private[tupson] def encode(value: A, out: JsonWriter, sort: Boolean): Unit = write(value, out, sort)
  }
}
