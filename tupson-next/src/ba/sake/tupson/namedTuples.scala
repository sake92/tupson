package ba.sake.tupson

import NamedTuple.AnyNamedTuple
import NamedTuple.Names
import NamedTuple.DropNames
import NamedTuple.NamedTuple
import NamedTuple.withNames
import scala.deriving.Mirror
import scala.reflect.ClassTag
import org.typelevel.jawn.ast.*

object namedTuples {
  // cache instances
  private val cache = scala.collection.mutable.Map.empty[ClassTag[?], JsonRW[?]]

  inline given [T <: AnyNamedTuple](using ct: ClassTag[T]): JsonRW[T] = {
    val fieldNames = compiletime.constValueTuple[Names[T]].productIterator.asInstanceOf[Iterator[String]].toSeq
    val fieldJsonRWs =
      compiletime.summonAll[Tuple.Map[DropNames[T], JsonRW]].productIterator.asInstanceOf[Iterator[JsonRW[Any]]].toSeq
    cache.getOrElseUpdate(ct, makeInstance[T](fieldNames, fieldJsonRWs)).asInstanceOf[JsonRW[T]]
  }

  private def makeInstance[T](fieldNames: Seq[String], fieldJsonRWs: Seq[JsonRW[Any]]) = {

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
}
