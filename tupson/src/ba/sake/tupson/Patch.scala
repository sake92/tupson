package ba.sake.tupson

import org.typelevel.jawn.ast.JNull
import org.typelevel.jawn.ast.JValue

enum Patch[+T]:
  case Set(value: T)
  case Clear
  case Keep

object Patch:
  given [T](using rw: JsonRW[T]): JsonRW[Patch[T]] with
    override def write(value: Patch[T]): JValue = value match
      case Patch.Set(v) => rw.write(v)
      case Patch.Clear  => JNull
      case Patch.Keep   => throw TupsonException("Cannot write Patch.Keep as JSON")

    override def parse(path: String, jValue: JValue): Patch[T] = jValue match
      case JNull => Patch.Clear
      case other => Patch.Set(rw.parse(path, other))

    override def default: Option[Patch[T]] = Some(Patch.Keep)
