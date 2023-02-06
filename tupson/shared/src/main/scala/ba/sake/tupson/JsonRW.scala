package ba.sake.tupson

import scala.reflect.ClassTag
import org.typelevel.jawn.ast.*
import magnolia1.{*, given}

trait JsonRW[T]:

  def write(value: T): JValue

  def parse(jValue: JValue): T

  /** Global default for `T` when key is missing in JSON.
    */
  def default: Option[T] = None
end JsonRW

object JsonRW extends AutoDerivation[JsonRW]:

  def apply[T](using rw: JsonRW[T]) = rw

  /* basic instances */
  given JsonRW[String] = new {
    override def write(value: String): JValue = JString(value)
    override def parse(jValue: JValue): String = jValue match
      case JString(s) => s
      case other      => typeMismatchError("String", other)
  }

  given JsonRW[Char] = new {
    override def write(value: Char): JValue = JString(value.toString)
    override def parse(jValue: JValue): Char = jValue match
      case JString(s) =>
        s.headOption.getOrElse(typeMismatchError("Char", jValue))
      case other => typeMismatchError("Char", other)
  }

  given JsonRW[Boolean] = new {
    override def write(value: Boolean): JValue = JBool(value)
    override def parse(jValue: JValue): Boolean = jValue match
      case JTrue  => true
      case JFalse => false
      case other  => typeMismatchError("Boolean", other)
  }

  given JsonRW[Float] = new {
    override def write(value: Float): JValue = DoubleNum(value)
    override def parse(jValue: JValue): Float = jValue match
      case DoubleNum(n) => n.toFloat
      case DeferNum(n)  => n.toFloat
      case other        => typeMismatchError("Float", other)
  }

  given JsonRW[Double] = new {
    override def write(value: Double): JValue = DoubleNum(value)
    override def parse(jValue: JValue): Double = jValue match
      case DoubleNum(n) => n.toDouble
      case DeferNum(n)  => n.toDouble
      case other        => typeMismatchError("Double", other)
  }

  given JsonRW[Int] = new {
    override def write(value: Int): JValue = LongNum(value)
    override def parse(jValue: JValue): Int = jValue match
      case LongNum(n)   => n.toInt
      case DeferLong(s) => s.toInt
      case other        => typeMismatchError("Int", other)
  }

  given JsonRW[Long] = new {
    override def write(value: Long): JValue = LongNum(value)
    override def parse(jValue: JValue): Long = jValue match
      case LongNum(n)   => n
      case DeferLong(s) => s.toLong
      case other        => typeMismatchError("Long", other)
  }

  given [T](using trw: JsonRW[T]): JsonRW[Option[T]] = new {
    override def write(value: Option[T]): JValue = value match
      case None    => JNull
      case Some(v) => trw.write(v)
    override def parse(jValue: JValue): Option[T] = jValue match
      case JNull => None
      case other => Option(trw.parse(jValue))
    override def default: Option[Option[T]] = Some(None)
  }

  given [T: ClassTag](using trw: JsonRW[T]): JsonRW[Array[T]] = new {
    override def write(value: Array[T]): JValue =
      JArray(value.map(trw.write))
    override def parse(jValue: JValue): Array[T] = jValue match
      case JArray(arr) => arr.map(trw.parse)
      case other       => typeMismatchError("Array", other)
  }

  given [T](using trw: JsonRW[T]): JsonRW[List[T]] = new {
    override def write(value: List[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(jValue: JValue): List[T] = jValue match
      case JArray(arr) => arr.toList.map(trw.parse)
      case other       => typeMismatchError("List", other)
  }

  given [T](using trw: JsonRW[T]): JsonRW[Seq[T]] = new {
    override def write(value: Seq[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(jValue: JValue): Seq[T] = jValue match
      case JArray(arr) => arr.toSeq.map(trw.parse)
      case other       => typeMismatchError("Seq", other)
  }

  given [T](using trw: JsonRW[T]): JsonRW[Map[String, T]] = new {
    override def write(value: Map[String, T]): JValue =
      val members = value.map((k, v) => k -> trw.write(v))
      JObject(members.to(scala.collection.mutable.Map))
    override def parse(jValue: JValue): Map[String, T] = jValue match
      case JObject(map) => map.mapValues(trw.parse).toMap
      case other        => typeMismatchError("Map", other)
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
    override def parse(jValue: JValue): T = jValue match
      case JObject(jsonMap) =>
        var missingRequiredKeys = Set.empty[String]
        ctx.params.foreach { param =>
          val keyPresent = jsonMap.contains(param.label)
          val hasGlobalDefault = param.typeclass.default.nonEmpty
          val hasLocalDefault = param.default.nonEmpty
          if !keyPresent && !hasGlobalDefault && !hasLocalDefault then
            missingRequiredKeys += param.label
        }
        if missingRequiredKeys.nonEmpty then
          throw MissingRequiredKeysException(missingRequiredKeys)

        ctx.construct { param =>
          jsonMap
            .get(param.label)
            .map(param.typeclass.parse) // TODO try catch
            .orElse(param.default)
            .orElse(param.typeclass.default)
            .get
        }
      case JString(enumName) =>
        println(ctx.typeInfo)
        ctx.rawConstruct(Seq()) // instantiate simple enum's case
      case other => typeMismatchError("JSON object", other)
  }

  override def split[T](ctx: SealedTrait[JsonRW, T]): JsonRW[T] = new {
    override def write(value: T): JValue =
      ctx.choose(value) { sub =>
        if ctx.isSingletonCasesEnum then {
          JsonRW[String].write(sub.typeInfo.short)
        } else {
          val subObject = sub.cast(value)
          val obj = sub.typeclass.write(subObject).asInstanceOf[JObject]
          obj.set("@type", JString(sub.typeInfo.short))
          obj
        }
      }
    override def parse(jValue: JValue): T = jValue match
      case JObject(jsonMap) if !ctx.isSingletonCasesEnum =>
        val typeName: String = jsonMap.get("@type") match
          case None => throw MissingRequiredKeysException(Set("@type"))
          case Some(JString(s)) => s
          case Some(other)      => typeMismatchError("@type: String", other)

        val subtypeNames = ctx.subtypes.map(_.typeInfo.short).map(t => s"'$t'")
        val subtype = ctx.subtypes.find(_.typeInfo.short == typeName) match
          case None =>
            throw TupsonException(
              s"Subtype not found: '$typeName'. Possible values: ${subtypeNames.mkString(", ")}"
            )
          case Some(st) => st

        subtype.typeclass.parse(jValue)
      case JString(enumName) if ctx.isSingletonCasesEnum =>
        val subtypeNames = ctx.subtypes.map(_.typeInfo.short).map(t => s"'$t'")
        val subtype = ctx.subtypes.find(_.typeInfo.short == enumName) match
          case None =>
            throw TupsonException(
              s"Enum value not found: '$enumName'. Possible values: ${subtypeNames.mkString(", ")}"
            )
          case Some(st) => st

        subtype.typeclass.parse(jValue)
      case other =>
        if ctx.isSingletonCasesEnum then typeMismatchError("String", other)
        else typeMismatchError("JSON object", other)
  }

  private def typeMismatchError(
      expectedType: String,
      jsonValue: JValue
  ): Nothing =
    val badJsonSnippet = jsonValue.render().take(100)
    throw TupsonException(
      s"Expected a ${expectedType} but got ${jsonValue.valueType}: '${badJsonSnippet}'"
    )

end JsonRW
