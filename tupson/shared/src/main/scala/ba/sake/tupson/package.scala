package ba.sake.tupson

import scala.annotation.StaticAnnotation as SA
import org.typelevel.jawn.ast.CanonicalRenderer

extension [T](value: T)(using rw: JsonRW[T]) {

  /** Converts T to its JSON representation.
    */
  def toJson: String =
    val jValue = rw.write(value)
    CanonicalRenderer.render(jValue)
}

/** Use a fixed name for a field.
  */
case class named(name: String) extends SA

class TupsonException(msg: String) extends Exception(msg)
