package ba.sake.tupson

import scala.compiletime.summonInline
import NamedTuple.AnyNamedTuple
import NamedTuple.Names
import NamedTuple.DropNames
import NamedTuple.NamedTuple
import NamedTuple.withNames
import scala.deriving.Mirror
import scala.reflect.ClassTag
import scala.quoted.*
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

}

private[tupson] object LowPriorityJsonRWInstances {
  def deriveUnionTC[T: Type](using Quotes): Expr[JsonRW[T]] = {
    import quotes.reflect.*
    TypeRepr.of[T] match {
      case OrType(left, right) =>
        left.asType match {
          case '[l] =>
            right.asType match {
              case '[r] =>
                '{
                  new JsonRW[T] {
                    override def write(value: T): JValue = value match {
                      case a: l => summonInline[JsonRW[l]].write(a)
                      case b: r => summonInline[JsonRW[r]].write(b)
                    }
                    override def parse(path: String, jValue: JValue): T = try {
                      summonInline[JsonRW[l]].parse(path, jValue).asInstanceOf[T]
                    } catch {
                      case _: TupsonException =>
                        summonInline[JsonRW[r]].parse(path, jValue).asInstanceOf[T]
                    }
                  }
                }
            }
        }
      case _ =>
        report.errorAndAbort(s"Cannot automatically derive JsonRW for non-union type ${Type.show[T]}")
    }
  }

  private def deriveNamedTupleTC[T](fieldNames: Seq[String], fieldJsonRWs: Seq[JsonRW[Any]]) = 
    new JsonRW[T] {
      override def write(value: T): JValue =
        val fieldValues = value.asInstanceOf[Tuple].productIterator.asInstanceOf[Iterator[Any]]
        val jsonFields = fieldNames.zip(fieldValues).zip(fieldJsonRWs).map { case ((name, v), rw) =>
          name -> rw.write(v)
        }
        JObject.fromSeq(jsonFields.toSeq)

      override def parse(path: String, jValue: JValue): T = jValue match {
        case JObject(fields) =>
          val fieldMap = fields.toMap
          val parsedValues = fieldNames.zip(fieldJsonRWs).map { case (name, rw) =>
            fieldMap.get(name) match {
              case Some(jv) => rw.parse(s"$path.$name", jv)
              case None     => throw TupsonException(s"Missing field '$name' at path '$path'")
            }
          }
          val tupleValue = Tuple.fromArray(parsedValues.toArray)
          withNames(tupleValue).asInstanceOf[T]
        case _ =>
          throw TupsonException(s"Expected JObject at path '$path', found: $jValue")
      }
    }
  
}

private[tupson] trait LowPriorityJsonRWInstances {

  // cache instances
  private val namedTupleTCsCache = scala.collection.mutable.Map.empty[ClassTag[?], JsonRW[?]]

  inline given autoderiveUnion[T]: JsonRW[T] = ${ LowPriorityJsonRWInstances.deriveUnionTC[T] }

  inline given autoderiveNamedTuple[T <: AnyNamedTuple](using ct: ClassTag[T]): JsonRW[T] = {
    val fieldNames = compiletime.constValueTuple[Names[T]].productIterator.asInstanceOf[Iterator[String]].toSeq
    val fieldJsonRWs =
      compiletime.summonAll[Tuple.Map[DropNames[T], JsonRW]].productIterator.asInstanceOf[Iterator[JsonRW[Any]]].toSeq
    namedTupleTCsCache.getOrElseUpdate(ct, LowPriorityJsonRWInstances.deriveNamedTupleTC[T](fieldNames, fieldJsonRWs)).asInstanceOf[JsonRW[T]]
  }

  // https://stackoverflow.com/questions/52430996/scala-passing-a-contravariant-type-as-an-implicit-parameter-does-not-choose-the
  given [T](using trw: JsonRW[T]): JsonRW[Seq[T]] with {
    override def write(value: Seq[T]): JValue =
      JArray(value.map(trw.write).toArray)
    override def parse(path: String, jValue: JValue): Seq[T] = jValue match
      case JArray(seq) => rethrowingKeysErrors(path, seq)
      case other       => JsonRW.typeMismatchError(path, "Seq", other)
    override def default: Option[Seq[T]] = Some(Seq.empty)
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
