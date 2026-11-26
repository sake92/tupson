package ba.sake.tupson

import scala.collection.mutable.ArrayBuffer
import com.github.plokhotnyuk.jsoniter_scala.core.*

private[tupson] final class TupsonReader[A](codec: JsonRW2[A])
    extends JsonValueCodec[Either[ParsingException, A]] {
  def read(json: String): A =
    parse(readFromString(json)(using this))

  def read(json: Array[Byte]): A =
    parse(readFromArray(json)(using this))

  def nullValue: Either[ParsingException, A] = null

  def decodeValue(in: JsonReader, default: Either[ParsingException, A]): Either[ParsingException, A] = {
    val ctx = new TupsonReader.DecodeContext
    val value = codec.decode(in, ctx)
    ctx.result(value)
  }

  def encodeValue(value: Either[ParsingException, A], out: JsonWriter): Unit =
    throw new UnsupportedOperationException("read adapter")

  private def parse(read: => Either[ParsingException, A]): A =
    try {
      read match {
        case Right(value) => value
        case Left(error)  => throw error
      }
    } catch {
      case e: JsonReaderException => throw TupsonException("JSON parsing exception", e)
    }
}

private[tupson] object TupsonReader {
  inline def readObject[A](in: JsonReader, ctx: DecodeContext)(inline read: A): A =
    readToken(in, ctx, "Object", null.asInstanceOf[A])(_ == '{') {
      in.nextToken()
      read
    }

  inline def readObjectFields(in: JsonReader)(inline readField: Int => Unit): Unit =
    if !in.isNextToken('}') then {
      in.rollbackToken()
      var more = true
      while more do {
        val length = in.readKeyAsCharBuf()
        readField(length)
        more = in.isNextToken(',')
      }
      if !in.isCurrentToken('}') then in.objectEndOrCommaError()
    }

  def isNumber(token: Byte): Boolean =
    token == '-' || (token >= '0' && token <= '9')

  inline def readToken[A](in: JsonReader, ctx: DecodeContext, expected: String, fallback: A)(
      inline accepts: Byte => Boolean
  )(inline read: A): A = {
    val token = in.nextToken()
    in.rollbackToken()
    if accepts(token) then read
    else {
      mismatch(in, ctx, token, expected)
      fallback
    }
  }

  private def mismatch(in: JsonReader, ctx: DecodeContext, token: Byte, expected: String): Unit = {
    val (actual, value) = token match {
      case '"'       => ("String", Some(JsonRW2.toJson(in.readString(null))().take(100)))
      case 't' | 'f' => ("Boolean", Some(in.readBoolean().toString))
      case 'n' =>
        in.nextToken()
        ("Null", Some(in.readNullOrError("null", "expected null")))
      case '[' | '{' =>
        skipValue(in)
        val kind = if token == '[' then "Array" else "Object"
        (kind, None)
      case b if isNumber(b) => ("Number", Some(in.readNumber(null).toString))
      case _ => in.decodeError("expected JSON value")
    }
    ctx.fail(s"should be $expected but it is $actual", value)
  }

  // Validate ignored values. JsonReader.skip can pass over malformed literals and containers.
  def skipValue(in: JsonReader, depth: Int = 0): Unit = {
    if depth == 128 then in.decodeError("skipped value exceeds nesting limit")
    in.nextToken() match {
      case start @ ('[' | '{') =>
        val end: Byte = if start == '[' then ']' else '}'
        if !in.isNextToken(end) then {
          in.rollbackToken()
          var more = true
          while more do {
            if start == '{' then in.readKeyAsCharBuf()
            skipValue(in, depth + 1)
            more = in.isNextToken(',')
          }
          if !in.isCurrentToken(end) then {
            if start == '[' then in.arrayEndOrCommaError()
            else in.objectEndOrCommaError()
          }
        }
      case 'n' => in.readNullOrError((), "expected null")
      case token =>
        in.rollbackToken()
        token match {
          case '"'       => in.readStringAsCharBuf()
          case 't' | 'f' => in.readBoolean()
          case b if isNumber(b) => in.readNumber(null)
          case _ => in.decodeError("expected JSON value")
        }
        ()
    }
  }

  final class DecodeContext {
    private var errors = Vector.empty[ParseError]
    private lazy val fields = ArrayBuffer.empty[String]

    def errorCount: Int = errors.size

    inline def withField[A](key: String)(inline read: A): A = {
      fields.addOne(key)
      try read
      finally fields.remove(fields.size - 1)
    }

    def fail(message: String, value: Option[Any] = None): Unit = {
      val path = if fields.isEmpty then "$" else fields.mkString("$.", ".", "")
      val error = ParseError(path, message, value)
      errors = errors.appended(error)
    }

    def result[A](value: A): Either[ParsingException, A] =
      if errors.isEmpty then Right(value) else Left(ParsingException(errors))
  }
}
