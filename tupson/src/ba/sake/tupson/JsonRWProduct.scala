package ba.sake.tupson

import scala.quoted.*
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}

private[tupson] object JsonRWProduct {
  inline def derived[A]: JsonRW[A] = ${ derive[A] }

  private def derive[A: Type](using Quotes): Expr[JsonRW[A]] =
    derive[A](None)

  def derive[A: Type](tag: Option[(String, String)])(using Quotes): Expr[JsonRW[A]] = {
    import quotes.reflect.*

    val model = TypeRepr.of[A].dealias
    val modelSymbol = model.typeSymbol
    val constructor = modelSymbol.primaryConstructor
    val parameters = constructor.paramSymss.filterNot(_.exists(_.isType))
    val accessors = modelSymbol.caseFields
    val singleton = if tag.nonEmpty && model <:< TypeRepr.of[scala.Singleton] then {
      val value = Expr.summon[ValueOf[A]].getOrElse {
        report.errorAndAbort(s"Cannot resolve tagged singleton ${Type.show[A]}")
      }
      Some('{ $value.value })
    } else None
    if singleton.isEmpty &&
      (!modelSymbol.flags.is(Flags.Case) || parameters.map(_.map(_.name)) != List(accessors.map(_.name)))
    then
      report.errorAndAbort(s"JsonRW requires a case class with one parameter list: ${Type.show[A]}")
    tag.foreach { (key, _) =>
      if accessors.exists(_.name == key) then
        report.errorAndAbort(s"Discriminator '$key' conflicts with a field of ${Type.show[A]}")
    }

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
      val tpe = model.memberType(accessor).widenTermRefByName
      tpe.asType match {
        case '[field] =>
          val codec = Expr.summon[JsonRW[field]].getOrElse {
            report.errorAndAbort(
              s"No JsonRW[${Type.show[field]}] for ${Type.show[A]}.${accessor.name}"
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

    def cacheCodecs(remaining: List[Field], cached: List[Field])(using Quotes): Expr[JsonRW[A]] =
      remaining match {
        case Nil => makeCodec(cached.reverse)
        case field :: tail =>
          field.tpe.asType match {
            case '[f] =>
              val codec = field.codec.asExprOf[JsonRW[f]]
              '{
                // A field codec may refer back to this product's given during initialization.
                lazy val fieldCodec = $codec
                ${ cacheCodecs(tail, field.copy(codec = 'fieldCodec.asTerm) :: cached) }
              }
          }
      }

    def makeCodec(fields: List[Field])(using Quotes): Expr[JsonRW[A]] = {
      '{
        new ProductCodec[A](
          (in, ctx) => TupsonReader.readObject[A](in, ctx) {
            ${ decodeFields(fields, Nil, 'in, 'ctx) }
          },
          (value, out, sort) => {
            out.writeObjectStart()
            if sort then ${ writeFields(fields, 'value, 'out, 'sort, sorted = true) }
            else ${ writeFields(fields, 'value, 'out, 'sort, sorted = false) }
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
      val construct = singleton.getOrElse {
        val method = applyTypeArguments(Select(New(TypeTree.of[A]), constructor))
        Apply(method, slots.map(_.value)).asExprOf[A]
      }
      val defaults = Expr.block(slots.map(fillMissing(_, ctx)), '{ () })
      val members = tag match {
        case Some((key, _)) =>
          val tagKey = Expr(key)
          '{
            var seenTag = false
            TupsonReader.readObjectFields($in) { length =>
              if $in.isCharBufEqualsTo(length, $tagKey) then {
                if seenTag then $ctx.duplicateKey($tagKey)
                seenTag = true
                TupsonReader.skipValue($in)
              } else ${ readField(slots, in, ctx, 'length) }
            }
          }
        case None =>
          '{
            TupsonReader.readObjectFields($in) { length =>
              ${ readField(slots, in, ctx, 'length) }
            }
          }
      }
      '{
        val before = $ctx.errorCount
        $members
        $defaults
        if $ctx.errorCount == before then $construct
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
            val codec = slot.field.codec.asExprOf[JsonRW[f]]
            val read = '{ $ctx.withField($key)($codec.decode($in, $ctx)) }
            val assign = Assign(slot.value, read.asTerm).asExprOf[Unit]
            val markSeen = Assign(slot.seen, Expr(true).asTerm).asExprOf[Unit]
            val seen = slot.seen.asExprOf[Boolean]
            '{
              if $in.isCharBufEqualsTo($length, $key) then {
                if $seen then $ctx.duplicateKey($key)
                $markSeen
                if $ctx.errorLimitReached then TupsonReader.skipValue($in)
                else $assign
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
              val codec = slot.field.codec.asExprOf[JsonRW[f]]
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

    def writeFields(
        fields: List[Field],
        value: Expr[A],
        out: Expr[JsonWriter],
        sort: Expr[Boolean],
        sorted: Boolean
    )(using Quotes): Expr[Unit] = {
      val writes = fields.map { field =>
        field.tpe.asType match {
          case '[f] =>
            val key = Expr(field.name)
            val codec = field.codec.asExprOf[JsonRW[f]]
            val member = Select(value.asTerm, field.accessor).asExprOf[f]
            val write = '{
              val fieldCodec = $codec
              val fieldValue = $member
              if fieldCodec.shouldWriteField(fieldValue) then {
                $out.writeKey($key)
                fieldCodec.encode(fieldValue, $out, $sort)
              }
            }
            field.name -> write
        }
      }
      val discriminator = tag.map { (name, label) =>
        val key = Expr(name)
        val value = Expr(label)
        name -> '{
          $out.writeKey($key)
          $out.writeVal($value)
        }
      }
      val members = discriminator.toList ::: writes
      val ordered = if sorted then members.sortBy(_._1) else members
      Expr.block(ordered.map(_._2), '{ () })
    }

    cacheCodecs(fields, Nil)
  }

  private[tupson] final class ProductCodec[A](
      read: (JsonReader, TupsonReader.DecodeContext) => A,
      write: (A, JsonWriter, Boolean) => Unit
  ) extends JsonRW[A] {
    private[tupson] def decode(in: JsonReader, ctx: TupsonReader.DecodeContext): A = read(in, ctx)
    private[tupson] def encode(value: A, out: JsonWriter, sort: Boolean): Unit = write(value, out, sort)
  }
}
