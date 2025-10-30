package ba.sake.tupson

import scala.collection.mutable.ArrayDeque
import scala.compiletime.summonInline
import NamedTuple.AnyNamedTuple
import NamedTuple.Names
import NamedTuple.DropNames
import NamedTuple.NamedTuple
import NamedTuple.withNames
import scala.deriving.Mirror
import scala.reflect.ClassTag
import scala.quoted.*
import org.typelevel.jawn.ast.*

object namedTuples {
  // TODO cache instances
  // private val namedTupleTCsCache = scala.collection.mutable.Map.empty[String, JsonRW[?]]

  inline given autoderiveNamedTuple[T <: AnyNamedTuple]: JsonRW[T] = {
    val fieldNames = compiletime.constValueTuple[Names[T]].productIterator.asInstanceOf[Iterator[String]].toSeq
    val fieldJsonRWs =
      compiletime.summonAll[Tuple.Map[DropNames[T], JsonRW]].productIterator.asInstanceOf[Iterator[JsonRW[Any]]].toSeq
    deriveNamedTupleTC[T](fieldNames, fieldJsonRWs)
    // namedTupleTCsCache.getOrElseUpdate(ct, deriveNamedTupleTC[T](fieldNames, fieldJsonRWs)).asInstanceOf[JsonRW[T]]
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
}

object unionTypes {

  inline given autoderiveUnion[T]: JsonRW[T] = ${ deriveUnionTC[T] }

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
}

private[tupson] trait LowPriorityJsonRWInstances {

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
