package ba.sake.tupson

import scala.collection.mutable.ArrayDeque
import scala.compiletime.*
import scala.deriving.*
import scala.quoted.*
import scala.reflect.ClassTag
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.Duration
import java.time.Period
import java.util.UUID
import org.typelevel.jawn.ast.*

trait JsonRW[T]:

  def write(value: T): JValue

  def parse(path: String, jValue: JValue): T

  /** Global default for `T` when key is missing in JSON.
    */
  def default: Option[T] = None

object JsonRW extends LowPriorityJsonRWInstances:

  def apply[T](using rw: JsonRW[T]) = rw

  // needed for enums macro
  given JsonRW[String] with {
    override def write(value: String): JValue = JString(value)
    override def parse(path: String, jValue: JValue): String = jValue match
      case JString(s) => s
      case other      => JsonRW.typeMismatchError(path, "String", other)
  }

  given JsonRW[JValue] with {
    override def write(value: JValue): JValue = value
    override def parse(path: String, jValue: JValue): JValue = jValue
  }

  /* basic instances */
  given JsonRW[Char] with {
    override def write(value: Char): JValue = JString(value.toString)
    override def parse(path: String, jValue: JValue): Char = jValue match
      case JString(s) =>
        s.headOption.getOrElse(JsonRW.typeMismatchError(path, "Char", jValue))
      case other => JsonRW.typeMismatchError(path, "Char", other)
  }

  given JsonRW[Boolean] with {
    override def write(value: Boolean): JValue = JBool(value)
    override def parse(path: String, jValue: JValue): Boolean = jValue match
      case JTrue  => true
      case JFalse => false
      case other  => JsonRW.typeMismatchError(path, "Boolean", other)
  }

  given JsonRW[Float] with {
    override def write(value: Float): JValue = DoubleNum(value)
    override def parse(path: String, jValue: JValue): Float = jValue match
      case DoubleNum(n) => n.toFloat
      case DeferNum(n)  => n.toFloat
      case LongNum(n)   => n.toFloat
      case DeferLong(s) => s.toFloat
      case other        => JsonRW.typeMismatchError(path, "Float", other)
  }

  given JsonRW[Double] with {
    override def write(value: Double): JValue = DoubleNum(value)
    override def parse(path: String, jValue: JValue): Double = jValue match
      case DoubleNum(n) => n.toDouble
      case DeferNum(n)  => n.toDouble
      case LongNum(n)   => n.toDouble
      case DeferLong(s) => s.toDouble
      case other        => JsonRW.typeMismatchError(path, "Double", other)
  }

  given JsonRW[Int] with {
    override def write(value: Int): JValue = LongNum(value)
    override def parse(path: String, jValue: JValue): Int = jValue match
      case LongNum(n)   => n.toInt
      case DeferLong(s) => s.toInt
      case other        => JsonRW.typeMismatchError(path, "Int", other)
  }

  given JsonRW[Long] with {
    override def write(value: Long): JValue = LongNum(value)
    override def parse(path: String, jValue: JValue): Long = jValue match
      case LongNum(n)   => n
      case DeferLong(s) => s.toLong
      case other        => JsonRW.typeMismatchError(path, "Long", other)
  }

  given JsonRW[UUID] with {
    override def write(value: UUID): JValue = JString(value.toString())
    override def parse(path: String, jValue: JValue): UUID = jValue match
      case JString(s) => UUID.fromString(s)
      case other      => JsonRW.typeMismatchError(path, "UUID", other)
  }

  // java.time
  given JsonRW[Instant] with {
    override def write(value: Instant): JValue = JString(value.toString)

    override def parse(path: String, jValue: JValue): Instant = jValue match
      case JString(s) => Instant.parse(s)
      case other      => JsonRW.typeMismatchError(path, "Instant", other)
  }

  given JsonRW[Duration] with {
    override def write(value: Duration): JValue = JString(value.toString)

    override def parse(path: String, jValue: JValue): Duration = jValue match
      case JString(s) => Duration.parse(s)
      case other      => JsonRW.typeMismatchError(path, "Duration", other)
  }

  given JsonRW[Period] with {
    override def write(value: Period): JValue = JString(value.toString)

    override def parse(path: String, jValue: JValue): Period = jValue match
      case JString(s) => Period.parse(s)
      case other      => JsonRW.typeMismatchError(path, "Period", other)
  }

  given [T](using trw: JsonRW[T]): JsonRW[Option[T]] with {
    override def write(value: Option[T]): JValue = value match
      case None    => JNull
      case Some(v) => trw.write(v)
    override def parse(path: String, jValue: JValue): Option[T] = jValue match
      case JNull => None
      case other => Option(trw.parse(path, jValue))
    override def default: Option[Option[T]] = Some(None)
  }

  /* collections */
  given [T](using trw: JsonRW[T]): JsonRW[List[T]] with {
    override def write(value: List[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(path: String, jValue: JValue): List[T] = jValue match
      case JArray(list) => rethrowingKeysErrors(path, list).toList
      case other        => JsonRW.typeMismatchError(path, "List", other)
    override def default: Option[List[T]] = Some(List.empty)
  }

  given [T: ClassTag](using trw: JsonRW[T]): JsonRW[Array[T]] with {
    override def write(value: Array[T]): JValue =
      JArray(value.map(trw.write))
    override def parse(path: String, jValue: JValue): Array[T] = jValue match
      case JArray(arr) => rethrowingKeysErrors(path, arr).toArray
      case other       => JsonRW.typeMismatchError(path, "Array", other)
    override def default: Option[Array[T]] = Some(Array.empty)
  }

  given [T](using trw: JsonRW[T]): JsonRW[Set[T]] with {
    override def write(value: Set[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(path: String, jValue: JValue): Set[T] = jValue match
      case JArray(set) => rethrowingKeysErrors(path, set).toSet
      case other       => JsonRW.typeMismatchError(path, "Set", other)
    override def default: Option[Set[T]] = Some(Set.empty)
  }

  given [T](using trw: JsonRW[T]): JsonRW[Map[String, T]] with {
    override def write(value: Map[String, T]): JValue =
      val members = value.map((k, v) => k -> trw.write(v))
      JObject(members.to(scala.collection.mutable.Map))
    override def parse(path: String, jValue: JValue): Map[String, T] =
      jValue match
        case JObject(map) =>
          map.map((k, v) => k -> trw.parse(s"$path.$k", v)).toMap
        case other => JsonRW.typeMismatchError(path, "Map", other)
    override def default: Option[Map[String, T]] = Some(Map.empty)
  }

  /* macro derived instances */
  inline def derived[T]: JsonRW[T] = ${ derivedMacro[T] }

  private def derivedMacro[T: Type](using Quotes): Expr[JsonRW[T]] = {
    import quotes.reflect.*

    val mirror: Expr[Mirror.Of[T]] = Expr.summon[Mirror.Of[T]].getOrElse {
      report.errorAndAbort(
        s"Cannot derive JsonRW[${Type.show[T]}] automatically because it is not a product or sum type"
      )
    }

    def isAnnotation(a: quotes.reflect.Term): Boolean =
      a.tpe.typeSymbol.maybeOwner.isNoSymbol ||
        a.tpe.typeSymbol.owner.fullName != "scala.annotation.internal"

    mirror match
      case '{
            type label <: Tuple;
            $m: Mirror.ProductOf[T] { type MirroredElemTypes = elementTypes; type MirroredElemLabels = `label` }
          } =>
        val rwInstancesExpr = summonInstances[T, elementTypes]
        val rwInstances = Expr.ofList(rwInstancesExpr)
        val labels = Expr(Type.valueOfTuple[label].map(_.toList.map(_.toString)).getOrElse(List.empty))
        val defaultValues = defaultValuesExpr[T]

        '{

          new JsonRW[T] {
            override def write(value: T): JValue = {
              val members = scala.collection.mutable.Map[String, JValue]()
              val valueAsProd = ${ 'value.asExprOf[Product] }
              $labels.zip(valueAsProd.productIterator).zip($rwInstances).foreach { case ((k, v), rw) =>
                members(k) = rw.asInstanceOf[JsonRW[Any]].write(v)
              }
              JObject(members)
            }

            override def parse(path: String, jValue: JValue): T = jValue match
              case JObject(jsonMap) =>
                val arguments = ArrayDeque.empty[Any]
                val keyErrors = ArrayDeque.empty[ParseError]
                val defaultValuesMap = $defaultValues.toMap

                $labels.zip($rwInstances).foreach { case (label, rw) =>
                  val keyPath = s"$path.$label"
                  val keyPresent = jsonMap.contains(label)
                  val hasGlobalDefault = rw.default.nonEmpty

                  val defaultOpt = defaultValuesMap(label)
                  val hasLocalDefault = defaultOpt.isDefined

                  if !keyPresent && !hasGlobalDefault && !hasLocalDefault then
                    keyErrors += ParseError(keyPath, "is missing")
                  else {
                    val argOpt = jsonMap
                      .get(label)
                      .flatMap { paramJValue =>
                        try {
                          Some(rw.parse(keyPath, paramJValue))
                        } catch {
                          case pe: ParsingException =>
                            keyErrors ++= pe.errors
                            None
                        }
                      }

                    argOpt
                      .orElse(defaultOpt.map(_()))
                      .orElse(rw.default)
                      .foreach { arg =>
                        arguments += arg
                      }
                  }
                }

                if keyErrors.nonEmpty then throw ParsingException(keyErrors.toSeq)

                $m.fromProduct(Tuple.fromArray(arguments.toArray))
              case JString(enumName) =>
                if $labels.isEmpty then $m.fromProduct(EmptyTuple) // instantiate enum's singleton case
                else typeMismatchError(path, "Object", jValue)
              case _ => typeMismatchError(path, "Object", jValue)

          }
        }

      case '{
            type label <: Tuple;
            $m: Mirror.SumOf[T] { type MirroredElemTypes = elementTypes; type MirroredElemLabels = `label` }
          } =>
        val labels = Expr(Type.valueOfTuple[label].map(_.toList.map(_.toString)).getOrElse(List.empty))

        val rwInstancesExpr = summonInstances[T, elementTypes]
        val rwInstances = Expr.ofList(rwInstancesExpr)

        val annotations = Expr.ofList(TypeRepr.of[T].typeSymbol.annotations.filter(isAnnotation).map(_.asExpr))

        val isSingleCasesEnum = isSingletonCasesEnum[T]

        '{
          val discrOpt = $annotations.find(_.isInstanceOf[discriminator]).map(_.asInstanceOf[discriminator])
          val discrName = discrOpt.map(_.name).getOrElse("@type")
          new JsonRW[T] {
            override def write(value: T): JValue =
              val index = $m.ordinal(value)
              val typeName = $labels(index)
              if $isSingleCasesEnum then {
                val label = $labels(index)
                JsonRW[String].write(label)
              } else {
                val rw = $rwInstances(index)
                val obj = rw.asInstanceOf[JsonRW[Any]].write(value).asInstanceOf[JObject]
                obj.set(discrName, JString(typeName))
                obj
              }

            override def parse(path: String, jValue: JValue): T = jValue match
              case JObject(jsonMap) if ! $isSingleCasesEnum =>
                val typeName: String = jsonMap.get(discrName) match
                  case None             => throw ParsingException(ParseError(discrName, "is missing"))
                  case Some(JString(s)) => s
                  case Some(other)      => typeMismatchError(path, s"$discrName: String", other)

                val idx = $labels.indexWhere(_ == typeName)
                if idx < 0 then
                  throw TupsonException(
                    s"Subtype not found: '$typeName'. Possible values: ${$labels.map(l => s"'$l'").mkString(", ")}"
                  )
                val rw = $rwInstances(idx)
                rw.parse(path, jValue).asInstanceOf[T]

              case JString(enumName) if $isSingleCasesEnum =>
                val idx = $labels.indexWhere(_ == enumName)
                if idx < 0 then
                  throw TupsonException(
                    s"Enum value not found: '$enumName'. Possible values: ${$labels.map(l => s"'$l'").mkString(", ")}"
                  )
                val rw = $rwInstances(idx)
                rw.parse(path, jValue).asInstanceOf[T]
              case other =>
                if $isSingleCasesEnum then typeMismatchError(path, "String", other)
                else typeMismatchError(path, "Object", other)
          }
        }
  }

  private def summonInstances[T: Type, Elems: Type](using Quotes): List[Expr[JsonRW[?]]] =
    Type.of[Elems] match
      case '[elem *: elems] => deriveOrSummon[T, elem] :: summonInstances[T, elems]
      case '[EmptyTuple]    => Nil

  private def deriveOrSummon[T: Type, Elem: Type](using Quotes): Expr[JsonRW[Elem]] =
    Type.of[Elem] match
      case '[T] => deriveRec[T, Elem]
      case _    => '{ summonInline[JsonRW[Elem]] }

  private def deriveRec[T: Type, Elem: Type](using Quotes): Expr[JsonRW[Elem]] =
    Type.of[T] match
      case '[Elem] => '{ error("infinite recursive derivation") }
      case _       => derivedMacro[Elem] // recursive derivation

  /* macro utils */
  private def isSingletonCasesEnum[T: Type](using Quotes): Expr[Boolean] =
    import quotes.reflect.*
    val ts = TypeRepr.of[T].typeSymbol
    Expr(ts.flags.is(Flags.Enum) && ts.companionClass.methodMember("values").nonEmpty)

  private def defaultValuesExpr[T: Type](using
      Quotes
  ): Expr[List[(String, Option[() => Any])]] =
    import quotes.reflect._
    def exprOfOption(
        oet: (Expr[String], Option[Expr[Any]])
    ): Expr[(String, Option[() => Any])] = oet match {
      case (label, None)     => Expr(label.valueOrAbort -> None)
      case (label, Some(et)) => '{ $label -> Some(() => $et) }
    }
    val tpe = TypeRepr.of[T].typeSymbol
    val terms = tpe.primaryConstructor.paramSymss.flatten
      .filter(_.isValDef)
      .zipWithIndex
      .map { case (field, i) =>
        exprOfOption {
          Expr(field.name) -> tpe.companionClass
            .declaredMethod(s"$$lessinit$$greater$$default$$${i + 1}")
            .headOption
            .flatMap(_.tree.asInstanceOf[DefDef].rhs)
            .map(_.asExprOf[Any])
        }
      }
    Expr.ofList(terms)

  /* generaul utils */
  private[tupson] def typeMismatchError(
      path: String,
      expectedType: String,
      jsonValue: JValue
  ): Nothing =
    val badJsonSnippet = jsonValue.render().take(100)
    throw ParsingException(
      ParseError(
        path,
        s"should be ${expectedType} but it is ${jsonValue.valueType.capitalize}",
        Some(badJsonSnippet)
      )
    )

end JsonRW
