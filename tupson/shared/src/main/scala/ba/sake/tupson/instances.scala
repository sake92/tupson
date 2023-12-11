package ba.sake.tupson

import scala.reflect.ClassTag
import scala.collection.mutable.ArrayDeque
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.Duration
import java.time.Period
import java.util.UUID
import org.typelevel.jawn.ast.*

private[tupson] trait JsonRWInstances extends LowPriorityJsonRWInstances {

  /* basic instances */
  given JsonRW[Char] = new {
    override def write(value: Char): JValue = JString(value.toString)
    override def parse(path: String, jValue: JValue): Char = jValue match
      case JString(s) =>
        s.headOption.getOrElse(JsonRW.typeMismatchError(path, "Char", jValue))
      case other => JsonRW.typeMismatchError(path, "Char", other)
  }

  given JsonRW[Boolean] = new {
    override def write(value: Boolean): JValue = JBool(value)
    override def parse(path: String, jValue: JValue): Boolean = jValue match
      case JTrue  => true
      case JFalse => false
      case other  => JsonRW.typeMismatchError(path, "Boolean", other)
  }

  given JsonRW[Float] = new {
    override def write(value: Float): JValue = DoubleNum(value)
    override def parse(path: String, jValue: JValue): Float = jValue match
      case DoubleNum(n) => n.toFloat
      case DeferNum(n)  => n.toFloat
      case other        => JsonRW.typeMismatchError(path, "Float", other)
  }

  given JsonRW[Double] = new {
    override def write(value: Double): JValue = DoubleNum(value)
    override def parse(path: String, jValue: JValue): Double = jValue match
      case DoubleNum(n) => n.toDouble
      case DeferNum(n)  => n.toDouble
      case other        => JsonRW.typeMismatchError(path, "Double", other)
  }

  given JsonRW[Int] = new {
    override def write(value: Int): JValue = LongNum(value)
    override def parse(path: String, jValue: JValue): Int = jValue match
      case LongNum(n)   => n.toInt
      case DeferLong(s) => s.toInt
      case other        => JsonRW.typeMismatchError(path, "Int", other)
  }

  given JsonRW[Long] = new {
    override def write(value: Long): JValue = LongNum(value)
    override def parse(path: String, jValue: JValue): Long = jValue match
      case LongNum(n)   => n
      case DeferLong(s) => s.toLong
      case other        => JsonRW.typeMismatchError(path, "Long", other)
  }

  given JsonRW[UUID] = new {
    override def write(value: UUID): JValue = JString(value.toString())
    override def parse(path: String, jValue: JValue): UUID = jValue match
      case JString(s) => UUID.fromString(s)
      case other      => JsonRW.typeMismatchError(path, "UUID", other)
  }

  // java.net
  // there is no RW for InetAddress because it could do host lookups.. :/
  given JsonRW[URI] = new {
    override def write(value: URI): JValue = JString(value.toString())
    override def parse(path: String, jValue: JValue): URI = jValue match
      case JString(s) => new URI(s)
      case other      => JsonRW.typeMismatchError(path, "URI", other)
  }

  given JsonRW[URL] = new {
    override def write(value: URL): JValue = JString(value.toString())
    override def parse(path: String, jValue: JValue): URL = jValue match
      case JString(s) => new URI(s).toURL()
      case other      => JsonRW.typeMismatchError(path, "URL", other)
  }

  // java.time
  given JsonRW[Instant] = new {
    override def write(value: Instant): JValue = JString(value.toString)

    override def parse(path: String, jValue: JValue): Instant = jValue match
      case JString(s) => Instant.parse(s)
      case other      => JsonRW.typeMismatchError(path, "Instant", other)
  }

  given JsonRW[Duration] = new {
    override def write(value: Duration): JValue = JString(value.toString)

    override def parse(path: String, jValue: JValue): Duration = jValue match
      case JString(s) => Duration.parse(s)
      case other      => JsonRW.typeMismatchError(path, "Duration", other)
  }

  given JsonRW[Period] = new {
    override def write(value: Period): JValue = JString(value.toString)

    override def parse(path: String, jValue: JValue): Period = jValue match
      case JString(s) => Period.parse(s)
      case other      => JsonRW.typeMismatchError(path, "Period", other)
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
  given [T](using trw: JsonRW[T]): JsonRW[List[T]] with {
    override def write(value: List[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(path: String, jValue: JValue): List[T] = jValue match
      case JArray(list) => rethrowingKeysErrors(path, list).toList
      case other        => JsonRW.typeMismatchError(path, "List", other)
  }

  given [T: ClassTag](using trw: JsonRW[T]): JsonRW[Array[T]] = new {
    override def write(value: Array[T]): JValue =
      JArray(value.map(trw.write))
    override def parse(path: String, jValue: JValue): Array[T] = jValue match
      case JArray(arr) => rethrowingKeysErrors(path, arr).toArray
      case other       => JsonRW.typeMismatchError(path, "Array", other)
  }

  given [T](using trw: JsonRW[T]): JsonRW[Set[T]] = new {
    override def write(value: Set[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(path: String, jValue: JValue): Set[T] = jValue match
      case JArray(set) => rethrowingKeysErrors(path, set).toSet
      case other       => JsonRW.typeMismatchError(path, "Set", other)
  }

  given [T](using trw: JsonRW[T]): JsonRW[Map[String, T]] = new {
    override def write(value: Map[String, T]): JValue =
      val members = value.map((k, v) => k -> trw.write(v))
      JObject(members.to(scala.collection.mutable.Map))
    override def parse(path: String, jValue: JValue): Map[String, T] =
      jValue match
        case JObject(map) =>
          map.map((k, v) => k -> trw.parse(s"$path.$k", v)).toMap
        case other => JsonRW.typeMismatchError(path, "Map", other)
  }

}

private[tupson] trait LowPriorityJsonRWInstances {

  // https://stackoverflow.com/questions/52430996/scala-passing-a-contravariant-type-as-an-implicit-parameter-does-not-choose-the
  given [T](using trw: JsonRW[T]): JsonRW[Seq[T]] with {
    override def write(value: Seq[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(path: String, jValue: JValue): Seq[T] = jValue match
      case JArray(seq) => rethrowingKeysErrors(path, seq)
      case other       => JsonRW.typeMismatchError(path, "Seq", other)
  }

  private[tupson] def rethrowingKeysErrors[T](path: String, values: Array[JValue])(using trw: JsonRW[T]): Seq[T] = {
    val parsedValues = ArrayDeque.empty[T]
    val keyErrors = ArrayDeque.empty[ParseError]
    values.zipWithIndex.foreach { case (v, i) =>
      val subPath = s"$path[$i]"
      try {
        parsedValues += trw.parse(subPath, v)
      } catch {
        case pe: ParsingException =>
          keyErrors ++= pe.errors
      }
    }
    if keyErrors.nonEmpty then throw ParsingException(keyErrors.toSeq)

    parsedValues.toSeq
  }
}
