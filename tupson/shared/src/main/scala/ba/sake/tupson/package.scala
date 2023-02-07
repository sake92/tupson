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

sealed class TupsonException(msg: String, cause: Throwable = null)
    extends Exception(msg, cause)

final class ParsingException(val keyErrors: Seq[(String, TupsonException)])
    extends TupsonException(
      keyErrors
        .map((k, e) => s"Key '$k': ${e.getMessage()}")
        .mkString("\n")
    )
