package ba.sake.tupson

import org.typelevel.jawn
import org.typelevel.jawn.ast.FastRenderer
import org.typelevel.jawn.ast.JParser

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
    case pe: ParsingException =>
      val parseErrors = pe.errors.map(e => e.withPath(s"$$.${e.path}"))
      throw ParsingException(parseErrors)
    case e: jawn.ParseException =>
      throw TupsonException("JSON parsing exception", e)
    case e: jawn.IncompleteParseException =>
      throw TupsonException("JSON parsing exception", e)
  }

}

sealed class TupsonException(msg: String, cause: Throwable = null)
    extends Exception(msg, cause)

final class TypeErrorException(msg: String, val value: Option[String]) extends TupsonException(msg)

final class ParsingException(val errors: Seq[ParseError])
    extends TupsonException(
      errors
        .map(_.text)
        .mkString("; ")
    )

case class ParseError(
    path: String,
    name: String,
    msg: String,
    value: Option[Any] = None // TODO delete ?
) {
  def withValue(v: Any) = copy(value = Some(v))
  def withPath(p: String) = copy(path = p)
  def withName(n: String) = copy(name = n)

  def text: String = value match {
    case Some(v) => s"Key '$path' with value '$v' $msg"
    case None    => s"Key '$path' $msg"
  }
}
