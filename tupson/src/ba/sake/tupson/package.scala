package ba.sake.tupson

import java.io.{InputStream, OutputStream}
import scala.annotation.StaticAnnotation

extension (json: String)
  def parseJson[A](using rw: JsonRW[A]): A = rw.readJson(json)

extension (json: Array[Byte])
  def parseJson[A](using rw: JsonRW[A]): A = rw.readJson(json)

extension (in: InputStream)
  /** Parses one UTF-8 JSON document and leaves the stream open. */
  def parseJson[A](using rw: JsonRW[A]): A = rw.readJson(in)

extension [A](value: A)
  def toJson(spaces: Int = 0, sort: Boolean = false)(using rw: JsonRW[A]): String =
    rw.renderJson(value, spaces, sort)

  /** Writes UTF-8 JSON and flushes the stream, leaving it open. */
  def writeJson(out: OutputStream, spaces: Int = 0, sort: Boolean = false)(using rw: JsonRW[A]): Unit =
    rw.writeTo(value, out, spaces, sort)

/* exceptions */
sealed class TupsonException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

private[tupson] final class DuplicateKeyException(path: String)
    extends TupsonException(s"Duplicate JSON key at '$path'")

final class ParsingException(val errors: Seq[ParseError])
    extends TupsonException(
      errors
        .map(_.text)
        .mkString("; ")
    )
object ParsingException {
  def apply(errors: Seq[ParseError]): ParsingException =
    new ParsingException(errors)
  def apply(pe: ParseError): ParsingException =
    new ParsingException(Seq(pe))
}

case class ParseError(
    path: String,
    msg: String,
    value: Option[Any] = None
) {
  def withPath(p: String) = copy(path = p)
  def withValue(v: Any) = copy(value = Some(v))

  def text: String = value match {
    case Some(v) => s"Key '$path' with value '$v' $msg"
    case None    => s"Key '$path' $msg"
  }
}

/* annotations */
case class discriminator(name: String) extends StaticAnnotation

/** Inlines a nested case class's derived fields into the containing JSON object. */
final class flatten extends StaticAnnotation

/** Captures unclaimed keys in a Map[String, Json] and writes them back as sibling fields. */
final class other extends StaticAnnotation
