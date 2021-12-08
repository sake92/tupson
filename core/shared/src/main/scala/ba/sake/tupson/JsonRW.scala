package ba.sake.tupson

import org.typelevel.jawn.ast.*
import shapeless3.deriving.*

trait JsonRW[T]:
  def write(value: T): JValue

object JsonRW:

  def apply[T](using rw: JsonRW[T]) = rw

  extension [T](value: T)(using rw: JsonRW[T])
    def toJson: String = CanonicalRenderer.render(rw.write(value))

  /* basic instances */
  given JsonRW[String] = str => JString(str)

  given JsonRW[Char] = c => JString(c.toString)

  given JsonRW[Boolean] = b => JBool(b)

  given JsonRW[Float] = n => JNum(n)

  given JsonRW[Double] = n => JNum(n)

  given JsonRW[Int] = n => JNum(n)

  given JsonRW[Long] = n => JNum(n)

  given [T](using trw: JsonRW[T]): JsonRW[Option[T]] = opt =>
    opt match
      case None => JNull
      case Some(v) => trw.write(v)

  given [T](using trw: JsonRW[T]): JsonRW[Array[T]] = elems =>
    JArray(elems.map(trw.write))

  given [T](using trw: JsonRW[T]): JsonRW[List[T]] = elems =>
    JArray(elems.map(trw.write).toArray)

  given [T](using trw: JsonRW[T]): JsonRW[Seq[T]] = elems =>
    JArray(elems.map(trw.write).toArray)

  /* derived instances */
  inline def derived[T](using gen: K0.Generic[T]): JsonRW[T] =
    gen.derive(rwGen, rwGenC)

  given rwGen[T](using
      inst: K0.ProductInstances[JsonRW, T],
      labelling: Labelling[T]
  ): JsonRW[T] with
    def write(x: T): JValue =
      val members = scala.collection.mutable.Map[String, JValue]()
      labelling.elemLabels.zipWithIndex.map { (label, i) =>
        val jvalue = inst.project(x)(i) {
          [t] => (st: JsonRW[t], pt: t) => st.write(pt)
        }
        members(label) = jvalue
      }
      JObject(members)

  given rwGenC[T](using
      inst: => K0.CoproductInstances[JsonRW, T]
  ): JsonRW[T] with
    def write(x: T): JValue =
      inst.fold(x){ [t] => (st: JsonRW[t], t: t) =>
        val obj = st.write(t).asInstanceOf[JObject]
        obj.set("@type", JString(x.getClass.getName))
        obj
    }

end JsonRW
