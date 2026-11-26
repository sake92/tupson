package ba.sake.tupson

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Builder
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
  private val maxErrors = 10

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

  def readDiscriminator(in: JsonReader, ctx: DecodeContext, key: String): String = {
    in.setMark()
    in.nextToken()
    var tag: String = null
    var found = false
    if !in.isNextToken('}') then {
      in.rollbackToken()
      var more = true
      while more && !found do {
        val length = in.readKeyAsCharBuf()
        if in.isCharBufEqualsTo(length, key) then {
          found = true
          tag = ctx.withField(key) {
            readToken(in, ctx, "String", null: String)(_ == '"')(in.readString(null))
          }
        } else {
          skipValue(in)
          more = in.isNextToken(',')
        }
      }
      if !found && !in.isCurrentToken('}') then in.objectEndOrCommaError()
    }
    // Release the reader's mark before the subtype codec reads the object.
    in.rollbackToMark()
    if !found then ctx.withField(key)(ctx.fail("is missing"))
    tag
  }

  def skipTaggedObject(in: JsonReader, ctx: DecodeContext, key: String): Unit = {
    in.nextToken()
    var seen = false
    readObjectFields(in) { length =>
      if in.isCharBufEqualsTo(length, key) then {
        if seen then ctx.duplicateKey(key)
        seen = true
      }
      skipValue(in)
    }
  }

  inline def readArray[A, C](
      in: JsonReader,
      ctx: DecodeContext,
      element: JsonRW2[A],
      expected: String,
      inline empty: C
  )(inline newBuilder: Builder[A, C]): C =
    readToken(in, ctx, expected, empty)(_ == '[') {
      in.nextToken()
      if in.isNextToken(']') then empty
      else {
        in.rollbackToken()
        val values = newBuilder
        val before = ctx.errorCount
        var index = 0
        var more = true
        while more do {
          if ctx.errorLimitReached then skipValue(in)
          else {
            val value = ctx.withIndex(index)(element.decode(in, ctx))
            if ctx.errorCount == before then values.addOne(value)
          }
          index = index + 1
          more = in.isNextToken(',')
        }
        if !in.isCurrentToken(']') then in.arrayEndOrCommaError()
        if ctx.errorCount == before then values.result() else empty
      }
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
    private lazy val pathEntries = ArrayBuffer.empty[PathEntry]
    private var pathDepth = 0

    def errorCount: Int = errors.size

    def errorLimitReached: Boolean = errorCount >= maxErrors

    def duplicateKey(key: String): Nothing =
      withField(key) {
        val path = renderPath()
        throw TupsonException(s"Duplicate JSON key at '$path'")
      }

    inline def withField[A](key: String)(inline read: A): A =
      withPathEntry(key, 0)(read)

    inline def withIndex[A](index: Int)(inline read: A): A =
      withPathEntry(null, index)(read)

    def fail(message: String, value: Option[Any] = None): Unit =
      if !errorLimitReached then {
        val path = renderPath()
        val error = ParseError(path, message, value)
        errors = errors.appended(error)
      }

    def result[A](value: A): Either[ParsingException, A] =
      if errors.isEmpty then Right(value) else Left(ParsingException(errors))

    private inline def withPathEntry[A](field: String, index: Int)(inline read: A): A = {
      pushPathEntry(field, index)
      try read
      finally pathDepth = pathDepth - 1
    }

    private def pushPathEntry(field: String, index: Int): Unit = {
      if pathDepth == pathEntries.size then pathEntries.addOne(new PathEntry)
      val entry = pathEntries(pathDepth)
      entry.field = field
      entry.index = index
      pathDepth = pathDepth + 1
    }

    private def renderPath(): String = {
      val path = new StringBuilder("$")
      var depth = 0
      while depth < pathDepth do {
        val entry = pathEntries(depth)
        if entry.field == null then path.append('[').append(entry.index).append(']')
        else path.append('.').append(entry.field)
        depth = depth + 1
      }
      path.toString
    }

    private final class PathEntry {
      var field: String = null
      var index: Int = 0
    }
  }
}
