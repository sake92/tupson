package ba.sake.tupson

import scala.util.Try
import org.typelevel.jawn.ast.*
import magnolia1.{*, given}
import scala.util.Failure
import scala.util.Success
import scala.reflect.ClassTag

trait JsonRW[T]:
  def write(value: T): JValue
  def parse(jValue: JValue): T = ???

object JsonRW extends AutoDerivation[JsonRW]:

  def apply[T](using rw: JsonRW[T]) = rw

  private def error(msg: String): Nothing =
    throw TupsonException(msg)

  /* basic instances */
  given JsonRW[String] = new {
    override def write(value: String): JValue = JString(value)
    override def parse(jValue: JValue): String = jValue match
      case JString(s) => s
      case other      => error(s"Expected a String but got ${other.valueType}")
  }

  given JsonRW[Char] = new {
    override def write(value: Char): JValue = JString(value.toString)
    override def parse(jValue: JValue): Char = jValue match
      case JString(s) => s.head
      case other      => error(s"Expected a Char but got ${other.valueType}")
  }

  given JsonRW[Boolean] = new {
    override def write(value: Boolean): JValue = JBool(value)
    override def parse(jValue: JValue): Boolean = jValue match
      case JTrue  => true
      case JFalse => false
      case other  => error(s"Expected a Boolean but got ${other.valueType}")
  }

  given JsonRW[Float] = new {
    override def write(value: Float): JValue = DoubleNum(value)
    override def parse(jValue: JValue): Float = jValue match
      case DoubleNum(n) => n.toFloat
      case DeferNum(n)  => n.toFloat
      case other        => error(s"Expected a Float but got ${other.valueType}")
  }

  given JsonRW[Double] = new {
    override def write(value: Double): JValue = DoubleNum(value)
    override def parse(jValue: JValue): Double = jValue match
      case DoubleNum(n) => n.toDouble
      case DeferNum(n)  => n.toDouble
      case other => error(s"Expected a Double but got ${other.valueType}")
  }

  given JsonRW[Int] = new {
    override def write(value: Int): JValue = LongNum(value)
    override def parse(jValue: JValue): Int = jValue match
      case LongNum(n)   => n.toInt
      case DeferLong(s) => s.toInt
      case other        => error(s"Expected an Int but got ${other.valueType}")
  }

  given JsonRW[Long] = new {
    override def write(value: Long): JValue = LongNum(value)
    override def parse(jValue: JValue): Long = jValue match
      case LongNum(n)   => n
      case DeferLong(s) => s.toLong
      case other        => error(s"Expected a Long but got ${other.valueType}")
  }

  given [T](using trw: JsonRW[T]): JsonRW[Option[T]] = new {
    override def write(value: Option[T]): JValue = value match
      case None    => JNull
      case Some(v) => trw.write(v)
    override def parse(jValue: JValue): Option[T] = jValue match
      case JNull => None
      case other => Option(trw.parse(jValue))
  }

  given [T: ClassTag](using trw: JsonRW[T]): JsonRW[Array[T]] = new {
    override def write(value: Array[T]): JValue =
      JArray(value.map(trw.write))
    override def parse(jValue: JValue): Array[T] = jValue match
      case JArray(arr) => arr.map(trw.parse)
      case other       => error(s"Expected an Array but got ${other.valueType}")
  }

  given [T](using trw: JsonRW[T]): JsonRW[List[T]] = new {
    override def write(value: List[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(jValue: JValue): List[T] = jValue match
      case JArray(arr) => arr.toList.map(trw.parse)
      case other       => error(s"Expected a List but got ${other.valueType}")
  }

  given [T](using trw: JsonRW[T]): JsonRW[Seq[T]] = new {
    override def write(value: Seq[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(jValue: JValue): Seq[T] = jValue match
      case JArray(arr) => arr.toSeq.map(trw.parse)
      case other       => error(s"Expected a Seq but got ${other.valueType}")
  }

  given [T](using trw: JsonRW[T]): JsonRW[Map[String, T]] = new {
    override def write(value: Map[String, T]): JValue =
      val members = value.map((k, v) => k -> trw.write(v))
      JObject(members.to(scala.collection.mutable.Map))
    override def parse(jValue: JValue): Map[String, T] = jValue match
      case JObject(map) => map.mapValues(trw.parse).toMap
      case other        => error(s"Expected a Map but got ${other.valueType}")
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
      case JObject(map) =>
        // TODO validate ALL KEYS, stonx MissingKeysException
        ctx.construct { param =>
          map
            .get(param.label)
            .map(param.typeclass.parse)
            .orElse(param.default)
            .getOrElse(throw MissingKeysException(Set(param.label)))
        }
      case other => error(s"Expected a JSON object but got ${other.valueType}")
  }

  override def split[T](ctx: SealedTrait[JsonRW, T]): JsonRW[T] = new {
    override def write(value: T): JValue =
      ctx.choose(value) { sub =>
        val subObject = sub.cast(value)
        val obj = sub.typeclass.write(subObject).asInstanceOf[JObject]
        obj.set("@type", JString(sub.typeInfo.full))
        obj
      }
    override def parse(jValue: JValue): T = jValue match
      case JObject(map) =>
        val typeName: String = map.get("@type") match
          case None             => throw MissingKeysException(Set("@type"))
          case Some(JString(s)) => s
          case Some(other) =>
            error(s"Expected a (@type: String) but got ${other.valueType}")

        val subtype = ctx.subtypes.find(_.typeInfo.full == typeName) match
          case None     => error(s"Subtype not found: $typeName")
          case Some(st) => st

        subtype.typeclass.parse(jValue)
      case other => error(s"Expected a JSON object but got ${other.valueType}")
  }

end JsonRW
