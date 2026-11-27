package ba.sake.tupson

import scala.collection.immutable.VectorMap
import scala.collection.mutable
import scala.quoted.*
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
import TupsonReader.DecodeContext

private[tupson] object JsonRWProduct {
  inline def derived[A]: JsonRW[A] = ${ derive[A] }

  private def derive[A: Type](using Quotes): Expr[JsonRW[A]] =
    derive[A](None)

  def derive[A: Type](tag: Option[(String, String)])(using Quotes): Expr[JsonRW[A]] = {
    import quotes.reflect.*

    sealed trait FieldKind
    case class Value(codec: Term) extends FieldKind
    case class Flatten(product: Product) extends FieldKind
    case object Other extends FieldKind

    case class Field(path: List[Symbol], tpe: TypeRepr, default: Option[Term], kind: FieldKind) {
      def name: String = path.last.name
      def location: String = path.map(_.name).mkString(".")
    }
    case class Product(tpe: TypeRepr, constructor: Symbol, fields: List[Field])
    case class Slot(value: Term, seen: Term)

    def applyTypeArguments(method: Term, model: TypeRepr): Term = (method.tpe.widen, model) match {
      case (_: PolyType, AppliedType(_, arguments)) => TypeApply(method, arguments.map(Inferred(_)))
      case _                                       => method
    }

    def constructorDefault(
        model: TypeRepr,
        parameter: Symbol,
        index: Int,
        location: String
    ): Option[Term] =
      if parameter.flags.is(Flags.HasDefault) then {
        val name = s"$$lessinit$$greater$$default$$${index + 1}"
        val symbol = model.typeSymbol
        val getter = symbol.companionClass.declaredMethod(name).headOption.getOrElse {
          report.errorAndAbort(s"Cannot find constructor default for ${Type.show[A]}.$location")
        }
        val method = applyTypeArguments(Select(Ref(symbol.companionModule), getter), model)
        Some(method.tpe.widen match {
          case _: MethodType => Apply(method, Nil)
          case _             => method
        })
      } else None

    def describe(model: TypeRepr, path: List[Symbol], ancestors: Set[Symbol]): Product = {
      val symbol = model.typeSymbol
      val location = (Type.show[A] :: path.map(_.name)).mkString(".")
      def invalidProduct(): Nothing =
        if path.isEmpty then
          report.errorAndAbort(s"JsonRW requires a case class with one parameter list: $location")
        else
          report.errorAndAbort(s"@flatten requires a case class with one parameter list: $location")

      if ancestors.contains(symbol) then report.errorAndAbort(s"Recursive @flatten at $location")
      if !symbol.flags.is(Flags.Case) || path.nonEmpty && model <:< TypeRepr.of[scala.Singleton] then invalidProduct()

      val constructor = symbol.primaryConstructor
      val parameters = constructor.paramSymss.filterNot(_.exists(_.isType))
      val accessors = symbol.caseFields
      if parameters.map(_.map(_.name)) != List(accessors.map(_.name)) then invalidProduct()

      val fields = accessors.zip(parameters.head).zipWithIndex.map { case ((accessor, parameter), index) =>
        val fieldPath = path :+ accessor
        val fieldLocation = s"$location.${accessor.name}"
        val tpe = model.memberType(accessor).widenTermRefByName
        val flattened = parameter.hasAnnotation(TypeRepr.of[flatten].typeSymbol)
        val otherFields = parameter.hasAnnotation(TypeRepr.of[other].typeSymbol)
        if flattened && otherFields then
          report.errorAndAbort(s"Cannot combine @flatten and @other on $fieldLocation")

        val kind = if flattened then {
          Flatten(describe(tpe.dealias, fieldPath, ancestors + symbol))
        } else if otherFields then {
          if path.nonEmpty then report.errorAndAbort(s"@other is not supported inside a flattened product: $fieldLocation")
          if !(tpe =:= TypeRepr.of[Map[String, Json]]) then
            report.errorAndAbort(s"@other requires Map[String, Json]: $fieldLocation")
          Other
        } else tpe.asType match {
          case '[f] =>
            val codec = Expr.summon[JsonRW[f]].getOrElse {
              report.errorAndAbort(s"No JsonRW[${Type.show[f]}] for $fieldLocation")
            }
            Value(codec.asTerm)
        }

        val default = constructorDefault(model, parameter, index, fieldPath.map(_.name).mkString("."))
        Field(fieldPath, tpe, default, kind)
      }
      Product(model, constructor, fields)
    }

    val model = TypeRepr.of[A].dealias
    val singleton = if tag.nonEmpty && model <:< TypeRepr.of[scala.Singleton] then {
      val value = Expr.summon[ValueOf[A]].getOrElse {
        report.errorAndAbort(s"Cannot resolve tagged singleton ${Type.show[A]}")
      }
      Some('{ $value.value })
    } else None
    val product = singleton match {
      case Some(_) => Product(model, Symbol.noSymbol, Nil)
      case None    => describe(model, Nil, Set.empty)
    }

    def wireFields(product: Product): List[Field] =
      product.fields.flatMap { field =>
        field.kind match {
          case Value(_)      => List(field)
          case Flatten(next) => wireFields(next)
          case Other         => Nil
        }
      }

    val fields = wireFields(product)
    val otherFields = product.fields.filter(_.kind == Other)
    if otherFields.size > 1 then report.errorAndAbort(s"Only one @other field is allowed in ${Type.show[A]}")

    fields.groupBy(_.name).values.find(_.size > 1).foreach { conflicts =>
      val names = conflicts.map(_.location).mkString(", ")
      report.errorAndAbort(s"Duplicate JSON key '${conflicts.head.name}' in ${Type.show[A]}: $names")
    }
    tag.foreach { (key, _) =>
      fields.find(_.name == key).foreach { field =>
        report.errorAndAbort(s"Discriminator '$key' conflicts with a field of ${Type.show[A]}: ${field.location}")
      }
    }

    def cacheCodecs(remaining: List[Field], codecs: Map[List[Symbol], Term])(using Quotes): Expr[JsonRW[A]] =
      remaining match {
        case Nil =>
          if otherFields.isEmpty then makeCodec(codecs, None)
          else {
            val keys = tag.map(_._1).toList ::: fields.map(_.name)
            val names = Expr.ofSeq(keys.map(Expr(_)))
            '{
              val reserved = Set.from($names)
              ${ makeCodec(codecs, Some('reserved)) }
            }
          }
        case field :: tail =>
          field.tpe.asType match {
            case '[f] =>
              val Value(term) = field.kind: @unchecked
              val codec = term.asExprOf[JsonRW[f]]
              '{
                // A field codec may refer back to this product's given during initialization.
                lazy val fieldCodec = $codec
                ${ cacheCodecs(tail, codecs.updated(field.path, 'fieldCodec.asTerm)) }
              }
          }
      }

    def makeCodec(codecs: Map[List[Symbol], Term], reserved: Option[Expr[Set[String]]])(using Quotes): Expr[JsonRW[A]] =
      '{
        new ProductCodec[A](
          (in, ctx) => TupsonReader.readObject[A](in, ctx) {
            ${ decodeFields(fields, Map.empty, codecs, 'in, 'ctx) }
          },
          (value, out, sort) => ${ writeObject(codecs, reserved, 'value, 'out, 'sort) }
        )
      }

    def decodeFields(
        remaining: List[Field],
        slots: Map[List[Symbol], Slot],
        codecs: Map[List[Symbol], Term],
        in: Expr[JsonReader],
        ctx: Expr[DecodeContext]
    )(using Quotes): Expr[A] = remaining match {
      case Nil =>
        if otherFields.isEmpty then readProduct(slots, codecs, None, in, ctx)
        else '{
          val extra = new ExtraFields
          ${ readProduct(slots, codecs, Some('extra), in, ctx) }
        }
      case field :: tail =>
        field.tpe.asType match {
          case '[f] =>
            '{
              var value: f = null.asInstanceOf[f]
              var seen = false
              ${ decodeFields(tail, slots.updated(field.path, Slot('value.asTerm, 'seen.asTerm)), codecs, in, ctx) }
            }
        }
    }

    def readProduct(
        slots: Map[List[Symbol], Slot],
        codecs: Map[List[Symbol], Term],
        extra: Option[Expr[ExtraFields]],
        in: Expr[JsonReader],
        ctx: Expr[DecodeContext]
    )(using Quotes): Expr[A] = {
      def readMembers(seenTag: Option[Expr[Boolean]])(using Quotes): Expr[Unit] =
        def dispatch(inputKey: Either[Expr[Int], Expr[String]], unknown: Expr[Unit])(using Quotes): Expr[Unit] =
          (tag, seenTag) match {
            case (Some((name, _)), Some(seen)) =>
              val key = Expr(name)
              val markSeen = Assign(seen.asTerm, Expr(true).asTerm).asExprOf[Unit]
              val matchesTag = keyMatches(in, inputKey, key)
              '{
                if $matchesTag then {
                  if $seen then $ctx.duplicateKey($key)
                  $markSeen
                  TupsonReader.skipValue($in)
                } else ${ readField(fields, slots, codecs, in, ctx, inputKey, unknown) }
              }
            case _ => readField(fields, slots, codecs, in, ctx, inputKey, unknown)
          }

        extra match {
          case None =>
            '{
              TupsonReader.readObjectFields($in) { length =>
                ${ dispatch(Left('length), '{ TupsonReader.skipValue($in) }) }
              }
            }
          case Some(rest) =>
            '{
              TupsonReader.readObjectMembers($in)($in.readKeyAsString()) { name =>
                ${ dispatch(Right('name), '{ $rest.read(name, $in, $ctx) }) }
              }
            }
        }

      val members = if tag.isEmpty then readMembers(None) else '{
        var seenTag = false
        ${ readMembers(Some('seenTag)) }
      }
      '{
        val before = $ctx.errorCount
        $members
        ${ singleton.getOrElse(resolveProduct[A](product, slots, codecs, extra, ctx, 'before)) }
      }
    }

    def keyMatches(in: Expr[JsonReader], inputKey: Either[Expr[Int], Expr[String]], key: Expr[String])(using
        Quotes
    ): Expr[Boolean] =
      inputKey match {
        case Left(length) => '{ $in.isCharBufEqualsTo($length, $key) }
        case Right(name)  => '{ $name == $key }
      }

    def readField(
        remaining: List[Field],
        slots: Map[List[Symbol], Slot],
        codecs: Map[List[Symbol], Term],
        in: Expr[JsonReader],
        ctx: Expr[DecodeContext],
        inputKey: Either[Expr[Int], Expr[String]],
        unknown: Expr[Unit]
    )(using Quotes): Expr[Unit] = remaining match {
      case Nil => unknown
      case field :: tail =>
        field.tpe.asType match {
          case '[f] =>
            val slot = slots(field.path)
            val key = Expr(field.name)
            val codec = codecs(field.path).asExprOf[JsonRW[f]]
            val read = '{ $ctx.withField($key)($codec.decode($in, $ctx)) }
            val assign = Assign(slot.value, read.asTerm).asExprOf[Unit]
            val markSeen = Assign(slot.seen, Expr(true).asTerm).asExprOf[Unit]
            val seen = slot.seen.asExprOf[Boolean]
            val matchesKey = keyMatches(in, inputKey, key)
            '{
              if $matchesKey then {
                if $seen then $ctx.duplicateKey($key)
                $markSeen
                if $ctx.errorLimitReached then TupsonReader.skipValue($in)
                else $assign
              } else ${ readField(tail, slots, codecs, in, ctx, inputKey, unknown) }
            }
        }
    }

    def resolveProduct[P: Type](
        product: Product,
        slots: Map[List[Symbol], Slot],
        codecs: Map[List[Symbol], Term],
        extra: Option[Expr[ExtraFields]],
        ctx: Expr[DecodeContext],
        before: Expr[Int]
    )(using Quotes): Expr[P] = {
      def resolveArguments(remaining: List[Field], args: List[Term])(using Quotes): Expr[P] =
        remaining match {
          case Nil =>
            val method = applyTypeArguments(Select(New(TypeTree.of[P]), product.constructor), product.tpe)
            val construct = Apply(method, args.reverse).asExprOf[P]
            '{ if $ctx.errorCount == $before then $construct else null.asInstanceOf[P] }
          case field :: tail =>
            field.tpe.asType match {
              case '[f] =>
                val argument = resolveField[f](field, slots, codecs, extra, ctx, before)
                '{
                  val value = $argument
                  ${ resolveArguments(tail, 'value.asTerm :: args) }
                }
            }
        }

      resolveArguments(product.fields, Nil)
    }

    def resolveField[F: Type](
        field: Field,
        slots: Map[List[Symbol], Slot],
        codecs: Map[List[Symbol], Term],
        extra: Option[Expr[ExtraFields]],
        ctx: Expr[DecodeContext],
        before: Expr[Int]
    )(using Quotes): Expr[F] = field.kind match {
      case Value(_) =>
        val slot = slots(field.path)
        val seen = slot.seen.asExprOf[Boolean]
        val value = slot.value.asExprOf[F]
        val missing = field.default match {
          case Some(default) => default.asExprOf[F]
          case None =>
            val codec = codecs(field.path).asExprOf[JsonRW[F]]
            val key = Expr(field.name)
            '{
              $codec.default match {
                case Some(default) => default
                case None =>
                  $ctx.withField($key)($ctx.fail("is missing"))
                  $value
              }
            }
        }
        '{ if !$seen && !$ctx.errorLimitReached then $missing else $value }

      case Flatten(nested) =>
        val read = resolveProduct[F](nested, slots, codecs, extra, ctx, before)
        field.default match {
          case None => read
          case Some(default) =>
            val seen = wireFields(nested).foldRight(Expr(false)) { (leaf, tail) =>
              val present = slots(leaf.path).seen.asExprOf[Boolean]
              '{ $present || $tail }
            }
            val fallback = default.asExprOf[F]
            '{ if $ctx.errorLimitReached then null.asInstanceOf[F] else if $seen then $read else $fallback }
        }

      case Other =>
        val rest = extra.get
        val captured = '{ $rest.result() }.asTerm.asExprOf[F]
        field.default match {
          case None => captured
          case Some(default) =>
            val fallback = default.asExprOf[F]
            '{ if $ctx.errorLimitReached then null.asInstanceOf[F] else if $rest.isEmpty then $fallback else $captured }
        }
    }

    def writeObject(
        codecs: Map[List[Symbol], Term],
        reserved: Option[Expr[Set[String]]],
        value: Expr[A],
        out: Expr[JsonWriter],
        sort: Expr[Boolean]
    )(using Quotes): Expr[Unit] = reserved match {
      case None =>
        '{
          $out.writeObjectStart()
          if $sort then ${ writeFields(codecs, value, out, sort, sorted = true, None) }
          else ${ writeFields(codecs, value, out, sort, sorted = false, None) }
          $out.writeObjectEnd()
        }
      case Some(keys) =>
        val member = Select(value.asTerm, otherFields.head.path.last).asExprOf[Map[String, Json]]
        '{
          val extra = new ExtraWriter($member, $keys, $out, $sort)
          $out.writeObjectStart()
          if $sort then ${ writeFields(codecs, value, out, sort, sorted = true, Some('extra)) }
          else ${ writeFields(codecs, value, out, sort, sorted = false, None) }
          extra.writeRemaining()
          $out.writeObjectEnd()
        }
    }

    def writeFields(
        codecs: Map[List[Symbol], Term],
        value: Expr[A],
        out: Expr[JsonWriter],
        sort: Expr[Boolean],
        sorted: Boolean,
        extra: Option[Expr[ExtraWriter]]
    )(using Quotes): Expr[Unit] = {
      val writes = fields.map { field =>
        field.tpe.asType match {
          case '[f] =>
            val key = Expr(field.name)
            val codec = codecs(field.path).asExprOf[JsonRW[f]]
            val member = field.path.foldLeft(value.asTerm)(Select(_, _)).asExprOf[f]
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
      val output = ordered.flatMap { (name, write) =>
        extra.toList.map(rest => '{ $rest.writeBefore(${ Expr(name) }) }) :+ write
      }
      Expr.block(output, '{ () })
    }

    cacheCodecs(fields, Map.empty)
  }

  // Keep the sort flag primitive across the generated writer call.
  private[tupson] trait ProductWriter[A] {
    def write(value: A, out: JsonWriter, sort: Boolean): Unit
  }

  private[tupson] final class ProductCodec[A](
      read: (JsonReader, DecodeContext) => A,
      writer: ProductWriter[A]
  ) extends JsonRW[A] {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): A = read(in, ctx)
    private[tupson] def encode(value: A, out: JsonWriter, sort: Boolean): Unit = writer.write(value, out, sort)
  }

  final class ExtraFields {
    private val fields = mutable.LinkedHashMap.empty[String, Json]

    def isEmpty: Boolean = fields.isEmpty

    def read(key: String, in: JsonReader, ctx: DecodeContext): Unit = {
      if fields.contains(key) then ctx.duplicateKey(key)
      val value = if ctx.errorLimitReached then {
        TupsonReader.skipValue(in)
        Json.Null
      } else ctx.withField(key)(JsonRW[Json].decode(in, ctx))
      fields.update(key, value)
    }

    def result(): Map[String, Json] = VectorMap.from(fields)
  }

  final class ExtraWriter(
      fields: Map[String, Json],
      reserved: Set[String],
      out: JsonWriter,
      sort: Boolean
  ) {
    private val entries = {
      fields.keysIterator.foreach { key =>
        if reserved.contains(key) then throw TupsonException(s"@other field conflicts with JSON key '$key'")
      }
      val ordered = if sort then fields.toVector.sortBy(_._1).iterator else fields.iterator
      ordered.buffered
    }

    def writeBefore(key: String): Unit =
      while entries.hasNext && entries.head._1.compareTo(key) < 0 do writeNext()

    def writeRemaining(): Unit =
      while entries.hasNext do writeNext()

    private def writeNext(): Unit = {
      val (key, value) = entries.next()
      out.writeKey(key)
      JsonRW[Json].encode(value, out, sort)
    }
  }
}
