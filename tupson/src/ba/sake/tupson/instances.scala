package ba.sake.tupson

import scala.collection.mutable.ArrayDeque
import scala.compiletime.summonInline
import scala.compiletime.summonFrom
import NamedTuple.AnyNamedTuple
import NamedTuple.Names
import NamedTuple.DropNames
import NamedTuple.NamedTuple
import NamedTuple.withNames
import scala.deriving.Mirror
import scala.reflect.ClassTag
import scala.quoted.*
import org.typelevel.jawn.ast.*


private[tupson] trait LowPriorityJsonRWInstances {

  inline given autoderiveLiteral[
      T <: String | Char | Boolean | Int | Long | Float | Double
  ](using valueOf: ValueOf[T]): JsonRW[T] =
    summonFrom {
      case ev: (T <:< String)  => literalRW[T, String](using valueOf, ev, summonInline[JsonRW[String]])
      case ev: (T <:< Char)    => literalCharRW[T](using valueOf, ev)
      case ev: (T <:< Boolean) => literalRW[T, Boolean](using valueOf, ev, summonInline[JsonRW[Boolean]])
      case ev: (T <:< Int)     => literalRW[T, Int](using valueOf, ev, summonInline[JsonRW[Int]])
      case ev: (T <:< Long)    => literalRW[T, Long](using valueOf, ev, summonInline[JsonRW[Long]])
      case ev: (T <:< Float)   => literalRW[T, Float](using valueOf, ev, summonInline[JsonRW[Float]])
      case ev: (T <:< Double)  => literalRW[T, Double](using valueOf, ev, summonInline[JsonRW[Double]])
    }

  private inline def literalRW[T, Wide](using
      valueOf: ValueOf[T],
      ev: T <:< Wide,
      rw: JsonRW[Wide]
  ): JsonRW[T] =
    new JsonRW[T] {
      private val expectedValue = valueOf.value
      private val expectedMsg = s"should be literal value '$expectedValue'"

      override def write(value: T): JValue = rw.write(ev(value))

      override def parse(path: String, jValue: JValue): T =
        val parsed = rw.parse(path, jValue)
        if parsed == expectedValue then expectedValue
        else throw ParsingException(ParseError(path, expectedMsg, Some(parsed)))
    }

  private inline def literalCharRW[T](using
      valueOf: ValueOf[T],
      ev: T <:< Char
  ): JsonRW[T] =
    new JsonRW[T] {
      private val expectedValue = valueOf.value
      private val expectedChar = ev(expectedValue)
      private val expectedMsg = s"should be literal value '$expectedValue'"

      override def write(value: T): JValue = JsonRW[Char].write(ev(value))

      override def parse(path: String, jValue: JValue): T =
        jValue match
          case JString(s) if s.length == 1 =>
            if s.head == expectedChar then expectedValue
            else throw ParsingException(ParseError(path, expectedMsg, Some(s.head)))
          case JString(s) =>
            throw ParsingException(ParseError(path, expectedMsg, Some(s)))
          case other =>
            JsonRW.typeMismatchError(path, "Char", other)
    }

  inline given autoderiveNamedTuple[T <: AnyNamedTuple](using T <:< AnyNamedTuple): JsonRW[T] = {
    val fieldNames = compiletime.constValueTuple[Names[T]].productIterator.asInstanceOf[Iterator[String]].toSeq
    val fieldJsonRWs =
      compiletime.summonAll[Tuple.Map[DropNames[T], JsonRW]].productIterator.asInstanceOf[Iterator[JsonRW[Any]]].toSeq
    deriveNamedTupleTC[T](fieldNames, fieldJsonRWs)
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
              case None     => throw ParsingException(ParseError(s"$path.$name", "is missing"))
            }
          }
          val tupleValue = Tuple.fromArray(parsedValues.toArray)
          withNames(tupleValue).asInstanceOf[T]
        case other =>
          JsonRW.typeMismatchError(path, "JObject", other)
      }
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
