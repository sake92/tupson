package ba.sake.tupson

import scala.reflect.ClassTag
import org.typelevel.jawn.ast.*
import magnolia2.{*, given}
import scala.collection.mutable.ListBuffer
import ba.sake.validation.FieldsValidationException
import ba.sake.validation.FieldValidationError

trait JsonRW[T]:

  def write(value: T): JValue

  def parse(path: String, jValue: JValue): T

  /** Global default for `T` when key is missing in JSON.
    */
  def default: Option[T] = None
end JsonRW

object JsonRW extends AutoDerivation[JsonRW]:

  def apply[T](using rw: JsonRW[T]) = rw

  /* basic instances */
  given JsonRW[String] = new {
    override def write(value: String): JValue = JString(value)
    override def parse(path: String, jValue: JValue): String = jValue match
      case JString(s) => s
      case other      => typeMismatchError(path, "String", other)
  }

  given JsonRW[Char] = new {
    override def write(value: Char): JValue = JString(value.toString)
    override def parse(path: String, jValue: JValue): Char = jValue match
      case JString(s) =>
        s.headOption.getOrElse(typeMismatchError(path, "Char", jValue))
      case other => typeMismatchError(path, "Char", other)
  }

  given JsonRW[Boolean] = new {
    override def write(value: Boolean): JValue = JBool(value)
    override def parse(path: String, jValue: JValue): Boolean = jValue match
      case JTrue  => true
      case JFalse => false
      case other  => typeMismatchError(path, "Boolean", other)
  }

  given JsonRW[Float] = new {
    override def write(value: Float): JValue = DoubleNum(value)
    override def parse(path: String, jValue: JValue): Float = jValue match
      case DoubleNum(n) => n.toFloat
      case DeferNum(n)  => n.toFloat
      case other        => typeMismatchError(path, "Float", other)
  }

  given JsonRW[Double] = new {
    override def write(value: Double): JValue = DoubleNum(value)
    override def parse(path: String, jValue: JValue): Double = jValue match
      case DoubleNum(n) => n.toDouble
      case DeferNum(n)  => n.toDouble
      case other        => typeMismatchError(path, "Double", other)
  }

  given JsonRW[Int] = new {
    override def write(value: Int): JValue = LongNum(value)
    override def parse(path: String, jValue: JValue): Int = jValue match
      case LongNum(n)   => n.toInt
      case DeferLong(s) => s.toInt
      case other        => typeMismatchError(path, "Int", other)
  }

  given JsonRW[Long] = new {
    override def write(value: Long): JValue = LongNum(value)
    override def parse(path: String, jValue: JValue): Long = jValue match
      case LongNum(n)   => n
      case DeferLong(s) => s.toLong
      case other        => typeMismatchError(path, "Long", other)
  }

  given [T](using trw: JsonRW[T]): JsonRW[Option[T]] = new {
    override def write(value: Option[T]): JValue = value match
      case None    => JNull
      case Some(v) => trw.write(v)
    override def parse(path: String, jValue: JValue): Option[T] = jValue match
      case JNull => None
      case other => Option(trw.parse(path, jValue))
    override def default: Option[Option[T]] = Some(None)
  }

  /* collections */
  private def rethrowingKeysErrors[T](parentPath: String, values: Seq[JValue])(using trw: JsonRW[T]): Seq[T] = {
    val parsedValues = ListBuffer.empty[T]
    val keyErrors = ListBuffer.empty[ParseError]
    val validationErrors = ListBuffer.empty[FieldValidationError]
    val res = values.zipWithIndex.map { (v, i) =>
      val path = s"$parentPath[$i]"
      try {
        parsedValues += trw.parse(path, v)
      } catch {
        case pe: ParsingException =>
          keyErrors ++= pe.errors
        case e: FieldsValidationException =>
          validationErrors ++= e.errors.map(_.withPath(path))
      }
    }
    if keyErrors.nonEmpty then throw ParsingException(keyErrors.toSeq)
    if validationErrors.nonEmpty then throw FieldsValidationException(validationErrors.toSeq)
    else parsedValues.toSeq
  }

  given [T: ClassTag](using trw: JsonRW[T]): JsonRW[Array[T]] = new {
    override def write(value: Array[T]): JValue =
      JArray(value.map(trw.write))
    override def parse(path: String, jValue: JValue): Array[T] = jValue match
      case JArray(arr) => rethrowingKeysErrors(path, arr).toArray
      case other       => typeMismatchError(path, "Array", other)
  }

  given [T](using trw: JsonRW[T]): JsonRW[List[T]] = new {
    override def write(value: List[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(path: String, jValue: JValue): List[T] = jValue match
      case JArray(list) => rethrowingKeysErrors(path, list).toList
      case other        => typeMismatchError(path, "List", other)
  }

  given [T](using trw: JsonRW[T]): JsonRW[Seq[T]] = new {
    override def write(value: Seq[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(path: String, jValue: JValue): Seq[T] = jValue match
      case JArray(seq) => rethrowingKeysErrors(path, seq)
      case other       => typeMismatchError(path, "Seq", other)
  }

  given [T](using trw: JsonRW[T]): JsonRW[Set[T]] = new {
    override def write(value: Set[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(path: String, jValue: JValue): Set[T] = jValue match
      case JArray(set) => rethrowingKeysErrors(path, set).toSet
      case other       => typeMismatchError(path, "Set", other)
  }

  given [T](using trw: JsonRW[T]): JsonRW[Map[String, T]] = new {
    override def write(value: Map[String, T]): JValue =
      val members = value.map((k, v) => k -> trw.write(v))
      JObject(members.to(scala.collection.mutable.Map))
    override def parse(path: String, jValue: JValue): Map[String, T] =
      jValue match
        case JObject(map) =>
          map.map((k, v) => k -> trw.parse(s"$path.$k", v)).toMap
        case other => typeMismatchError(path, "Map", other)
  }

  /* derived instances */
  override def join[T](ctx: CaseClass[Typeclass, T]): JsonRW[T] = new {
    override def write(value: T): JValue =
      val members = scala.collection.mutable.Map[String, JValue]()
      ctx.params
        .map { param =>
          val p = param.deref(value)
          val jValue = param.typeclass.write(p)
          members(param.label) = jValue
        }
      JObject(members)
    override def parse(parentPath: String, jValue: JValue): T = jValue match
      case JObject(jsonMap) =>
        val arguments = ListBuffer.empty[Any]
        val keyErrors = ListBuffer.empty[ParseError]
        val validationErrors = ListBuffer.empty[FieldValidationError]
        ctx.params.foreach { param =>
          val path = s"$parentPath.${param.label}"
          val keyPresent = jsonMap.contains(param.label)
          val hasGlobalDefault = param.typeclass.default.nonEmpty
          val hasLocalDefault = param.default.nonEmpty
          if !keyPresent && !hasGlobalDefault && !hasLocalDefault then keyErrors += ParseError(path, "is missing")
          else
            arguments += jsonMap
              .get(param.label)
              .map { paramJValue =>
                try {
                  param.typeclass.parse(path, paramJValue)
                } catch {
                  case pe: ParsingException =>
                    keyErrors ++= pe.errors
                  case e: FieldsValidationException =>
                    validationErrors ++= e.errors.map(_.withPath(path))
                }
              }
              .orElse(param.default)
              .orElse(param.typeclass.default)
              .get
        }

        if keyErrors.nonEmpty then throw ParsingException(keyErrors.toSeq)
        if validationErrors.nonEmpty then throw FieldsValidationException(validationErrors.toSeq)

        ctx.rawConstruct(arguments.toSeq)
      case JString(enumName) =>
        if ctx.params.isEmpty then ctx.rawConstruct(Seq()) // instantiate enum's singleton case
        else typeMismatchError(parentPath, "Object", jValue)
      case _ => typeMismatchError(parentPath, "Object", jValue)
  }

  override def split[T](ctx: SealedTrait[JsonRW, T]): JsonRW[T] = new {
    override def write(value: T): JValue =
      ctx.choose(value) { sub =>
        if ctx.isSingletonCasesEnum then {
          JsonRW[String].write(sub.typeInfo.short)
        } else {
          val subObject = sub.cast(value)
          val obj = sub.typeclass.write(subObject).asInstanceOf[JObject]
          obj.set("@type", JString(sub.typeInfo.short)) // TODO annotation
          obj
        }
      }
    override def parse(path: String, jValue: JValue): T = jValue match
      case JObject(jsonMap) if !ctx.isSingletonCasesEnum =>
        val typeName: String = jsonMap.get("@type") match
          case None =>
            throw ParsingException(ParseError("@type", "is missing"))
          case Some(JString(s)) => s
          case Some(other)      => typeMismatchError(path, "@type: String", other)

        val subtypeNames = ctx.subtypes.map(_.typeInfo.short).map(t => s"'$t'")
        val subtype = ctx.subtypes.find(_.typeInfo.short == typeName) match
          case None =>
            throw TupsonException(
              s"Subtype not found: '$typeName'. Possible values: ${subtypeNames.mkString(", ")}"
            )
          case Some(st) => st

        subtype.typeclass.parse(path, jValue)
      case JString(enumName) if ctx.isSingletonCasesEnum =>
        val subtypeNames = ctx.subtypes.map(_.typeInfo.short).map(t => s"'$t'")
        val subtype = ctx.subtypes.find(_.typeInfo.short == enumName) match
          case None =>
            throw TupsonException(
              s"Enum value not found: '$enumName'. Possible values: ${subtypeNames.mkString(", ")}"
            )
          case Some(st) => st

        subtype.typeclass.parse(path, jValue)
      case other =>
        if ctx.isSingletonCasesEnum then typeMismatchError(path, "String", other)
        else typeMismatchError(path, "Object", other)
  }

  private def typeMismatchError(
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
