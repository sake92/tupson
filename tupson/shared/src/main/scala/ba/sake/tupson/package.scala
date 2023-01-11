package ba.sake.tupson

import scala.annotation.StaticAnnotation
import org.typelevel.jawn.ast.FastRenderer

extension [T](value: T)(using rw: JsonRW[T]) {

  /** Converts T to its JSON representation. */
  def toJson: String =
    val jValue = rw.write(value)
    FastRenderer.render(jValue)
}

/** Use a fixed name for a field.
  */
case class named(name: String) extends StaticAnnotation

class TupsonException(msg: String) extends Exception(msg)
