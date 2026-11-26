package ba.sake.tupson

import com.github.plokhotnyuk.jsoniter_scala.core.*

/** A JSON codec that reads and writes directly through the backend. */
sealed abstract class JsonRW2[A] {
  private val reader = new JsonValueCodec[Either[ParsingException, A]] {
    def nullValue: Either[ParsingException, A] = null

    def decodeValue(in: JsonReader, default: Either[ParsingException, A]): Either[ParsingException, A] = {
      val ctx = new JsonRW2.DecodeContext
      val value = decode(in, ctx)
      ctx.result(value)
    }

    def encodeValue(value: Either[ParsingException, A], out: JsonWriter): Unit =
      throw new UnsupportedOperationException("read adapter")
  }

  private val sortedWriter = writer(sort = true)
  private val unsortedWriter = writer(sort = false)

  final def bimap[B](read: A => B)(write: B => A): JsonRW2[B] =
    mapped((value, _) => read(value), write)

  final def emap[B](read: A => Either[String, B])(write: B => A): JsonRW2[B] =
    mapped(
      (value, ctx) =>
        read(value) match {
          case Right(result) => result
          case Left(message) =>
            ctx.fail(message)
            null.asInstanceOf[B]
        },
      write
    )

  private[tupson] def decode(in: JsonReader, ctx: JsonRW2.DecodeContext): A
  private[tupson] def encode(value: A, out: JsonWriter, sort: Boolean): Unit

  private def read(json: String): A =
    parse(readFromString(json)(using reader))

  private def read(json: Array[Byte]): A =
    parse(readFromArray(json)(using reader))

  private def render(value: A, spaces: Int, sort: Boolean): String = {
    val config = if spaces <= 0 then WriterConfig else WriterConfig.withIndentionStep(spaces)
    writeToString(value, config)(using if sort then sortedWriter else unsortedWriter)
  }

  private def mapped[B](read: (A, JsonRW2.DecodeContext) => B, write: B => A): JsonRW2[B] =
    new JsonRW2[B] {
      private[tupson] def decode(in: JsonReader, ctx: JsonRW2.DecodeContext): B = {
        val before = ctx.errorCount
        val value = JsonRW2.this.decode(in, ctx)
        if ctx.errorCount == before then read(value, ctx)
        else null.asInstanceOf[B]
      }

      private[tupson] def encode(value: B, out: JsonWriter, sort: Boolean): Unit =
        JsonRW2.this.encode(write(value), out, sort)
    }

  private def parse(read: => Either[ParsingException, A]): A =
    try {
      read match {
        case Right(value) => value
        case Left(error)  => throw error
      }
    } catch {
      case e: JsonReaderException => throw TupsonException("JSON parsing exception", e)
    }

  private def writer(sort: Boolean): JsonValueCodec[A] = new JsonValueCodec[A] {
    def nullValue: A = null.asInstanceOf[A]
    def decodeValue(in: JsonReader, default: A): A =
      throw new UnsupportedOperationException("write adapter")
    def encodeValue(value: A, out: JsonWriter): Unit = encode(value, out, sort)
  }
}

object JsonRW2 {
  def apply[A](using rw: JsonRW2[A]): JsonRW2[A] = rw

  extension (json: String)
    def parseJson[A](using rw: JsonRW2[A]): A = rw.read(json)

  extension (json: Array[Byte])
    def parseJson[A](using rw: JsonRW2[A]): A = rw.read(json)

  extension [A](value: A)
    def toJson(spaces: Int = 0, sort: Boolean = false)(using rw: JsonRW2[A]): String =
      rw.render(value, spaces, sort)

  given JsonRW2[String] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): String =
      readToken(in, ctx, "String", null: String)(_ == '"')(in.readString(null))

    private[tupson] def encode(value: String, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  given JsonRW2[Char] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Char =
      readToken(in, ctx, "Char", 0.toChar)(_ == '"') {
        val value = in.readString(null)
        if value.nonEmpty then value.charAt(0)
        else {
          ctx.fail("should be Char but it is String", Some("\"\""))
          0.toChar
        }
      }

    private[tupson] def encode(value: Char, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  given JsonRW2[Boolean] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Boolean =
      readToken(in, ctx, "Boolean", false)(b => b == 't' || b == 'f')(in.readBoolean())

    private[tupson] def encode(value: Boolean, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  given JsonRW2[Int] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Int =
      readToken(in, ctx, "Int", 0)(isNumber)(in.readInt())

    private[tupson] def encode(value: Int, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  given JsonRW2[Long] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Long =
      readToken(in, ctx, "Long", 0L)(isNumber)(in.readLong())

    private[tupson] def encode(value: Long, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  given JsonRW2[Float] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Float =
      readToken(in, ctx, "Float", 0f)(isNumber)(in.readFloat())

    private[tupson] def encode(value: Float, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value.toDouble)
  }

  given JsonRW2[Double] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Double =
      readToken(in, ctx, "Double", 0d)(isNumber)(in.readDouble())

    private[tupson] def encode(value: Double, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  private[tupson] final class DecodeContext {
    private var errors = Vector.empty[ParseError]

    def errorCount: Int = errors.size
    def fail(message: String, value: Option[Any] = None): Unit = {
      val error = ParseError("$", message, value)
      errors = errors.appended(error)
    }

    def result[A](value: A): Either[ParsingException, A] =
      if errors.isEmpty then Right(value) else Left(ParsingException(errors))
  }

  private def isNumber(token: Byte): Boolean =
    token == '-' || (token >= '0' && token <= '9')

  private inline def readToken[A](in: JsonReader, ctx: DecodeContext, expected: String, fallback: A)(
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
      case '"'       => ("String", Some(JsonRW2[String].render(in.readString(null), spaces = 0, sort = false).take(100)))
      case 't' | 'f' => ("Boolean", Some(in.readBoolean().toString))
      case 'n'       => ("Null", Some(in.readNullOrError("null", "expected null")))
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
  private def skipValue(in: JsonReader, depth: Int = 0): Unit = {
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
      case token =>
        in.rollbackToken()
        token match {
          case '"'       => in.readStringAsCharBuf()
          case 't' | 'f' => in.readBoolean()
          case 'n'       => in.readNullOrError((), "expected null")
          case b if isNumber(b) => in.readNumber(null)
          case _ => in.decodeError("expected JSON value")
        }
        ()
    }
  }
}
