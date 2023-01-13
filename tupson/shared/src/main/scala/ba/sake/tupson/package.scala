package ba.sake.tupson

import scala.annotation.StaticAnnotation
import org.typelevel.jawn.ast.FastRenderer
import org.typelevel.jawn.ast.JParser

extension [T](value: T)(using rw: JsonRW[T]) {
  def toJson: String =
    val jValue = rw.write(value)
    FastRenderer.render(jValue)
}

extension (strValue: String) {
  def parseJson[T](using rw: JsonRW[T]): T =
    // TODO try catch exceptions..
    // ParseException(msg: String, index: Int, line: Int, col: Int) extends Exception(msg)
    // IncompleteParseException
    val jValue = JParser.parseUnsafe(strValue)
    rw.parse(jValue)
}

class TupsonException(msg: String) extends Exception(msg)

class MissingKeysException(val keys: Set[String])
    extends TupsonException(s"Missing keys: $keys")
