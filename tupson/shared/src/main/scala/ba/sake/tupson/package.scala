package ba.sake.tupson

import org.typelevel.jawn.ast.FastRenderer
import org.typelevel.jawn.ast.JParser
import org.typelevel.jawn.ParseException
import org.typelevel.jawn.IncompleteParseException

extension [T](value: T)(using rw: JsonRW[T]) {
  def toJson: String =
    val jValue = rw.write(value)
    FastRenderer.render(jValue)
}

extension (strValue: String) {
  def parseJson[T](using rw: JsonRW[T]): T = try {
    val jValue = JParser.parseUnsafe(strValue)
    rw.parse(jValue)
  } catch {
    case e: ParseException =>
      throw TupsonException("JSON parsing exception", e)
    case e: IncompleteParseException =>
      throw TupsonException("JSON parsing exception", e)
  }

}

class TupsonException(msg: String, cause: Throwable = null)
    extends Exception(msg, cause)

class MissingRequiredKeysException(val keys: Set[String])
    extends TupsonException(s"Missing required keys: ${keys.mkString(", ")}")
