import ba.sake.tupson.{*, given}
import org.typelevel.jawn.ast.*

/** A field update for a JSON PATCH-style API.
  *
  * A missing key is [[Patch.Keep]], `null` is [[Patch.Clear]], and a JSON value is [[Patch.Set]].
  */
enum Patch[+T]:
  case Set(value: T)
  case Clear
  case Keep

object Patch:
  given [T](using valueRW: JsonRW[T]): JsonRW[Patch[T]] with
    override def write(value: Patch[T]): JValue = value match
      case Set(value) => valueRW.write(value)
      case Clear      => JNull
      case Keep =>
        throw TupsonException("Patch.Keep can only be written as an object field")

    override def shouldWriteField(value: Patch[T]): Boolean = value != Keep

    override def parse(path: String, jValue: JValue): Patch[T] = jValue match
      case JNull => Clear
      case other => Set(valueRW.parse(path, other))

    override def default: Option[Patch[T]] = Some(Keep)

case class UserPatch(name: Patch[String], address: Patch[String]) derives JsonRW

@main def patchExample: Unit =
  val userPatch = """{ "name": "Ada", "address": null }""".parseJson[UserPatch]
  println(userPatch) // UserPatch(Set(Ada), Clear)

  val noChanges = UserPatch(Patch.Keep, Patch.Keep)
  println(noChanges.toJson(spaces = 0)) // {}
