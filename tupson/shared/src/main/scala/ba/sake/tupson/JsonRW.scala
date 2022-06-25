package ba.sake.tupson

import org.typelevel.jawn.ast.*
import magnolia1.{*, given}

trait JsonRW[T]:
  def write(value: T): JValue

object JsonRW extends AutoDerivation[JsonRW]:

  def apply[T](using rw: JsonRW[T]) = rw

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
      case None    => JNull
      case Some(v) => trw.write(v)

  given [T](using trw: JsonRW[T]): JsonRW[Array[T]] = elems =>
    JArray(elems.map(trw.write))

  given [T](using trw: JsonRW[T]): JsonRW[List[T]] = elems =>
    JArray(elems.map(trw.write).toArray)

  given [T](using trw: JsonRW[T]): JsonRW[Seq[T]] = elems =>
    JArray(elems.map(trw.write).toArray)

  given [T](using trw: JsonRW[T]): JsonRW[Map[String, T]] = elems => {
    val members = elems.map((k, v) => k -> trw.write(v))
    JObject(members.to(scala.collection.mutable.Map))
  }

  /* derived instances */
  override def join[T](ctx: CaseClass[Typeclass, T]): JsonRW[T] = value =>
    val members = scala.collection.mutable.Map[String, JValue]()
    ctx.params
      .map { param =>

        val namedOpt = param.annotations
          .find(_.getClass == classOf[named])
          .map(_.asInstanceOf[named])
        val label = namedOpt.map(_.name).getOrElse(param.label)
        if members.keySet.contains(label) then
          throw TupsonException(s"Duplicate JSON name: ${label}")

        val p = param.deref(value)
        val jvalue = param.typeclass.write(p)

        members(label) = jvalue
      }
    JObject(members)

  override def split[T](ctx: SealedTrait[JsonRW, T]): JsonRW[T] = value =>
    ctx.choose(value) { sub =>
      val subObject = sub.cast(value)
      val obj = sub.typeclass.write(subObject).asInstanceOf[JObject]
      obj.set("@type", JString(sub.typeInfo.full))
      obj
    }

end JsonRW
