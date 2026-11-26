package ba.sake.tupson

import java.io.OutputStream
import java.math.{BigDecimal, BigInteger}
import java.net.URI
import java.time.{Duration, Instant, LocalDate, LocalDateTime, LocalTime, Period}
import java.time.{OffsetDateTime, OffsetTime, ZoneId, ZoneOffset, ZonedDateTime}
import java.util.UUID
import scala.collection.mutable.HashSet
import scala.deriving.Mirror
import scala.NamedTuple.AnyNamedTuple
import scala.reflect.ClassTag
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
import TupsonReader.*

/** A JSON codec that reads and writes directly through the backend.
  *
  * Decoding reports up to ten validation errors per document. Syntax errors are fatal.
  */
abstract class JsonRW2[A] private[tupson] () {
  private val reader = new TupsonReader(this)
  private val sortedWriter = new TupsonWriter(this, sort = true)
  private val unsortedWriter = new TupsonWriter(this, sort = false)

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

  private[tupson] def decode(in: JsonReader, ctx: DecodeContext): A
  private[tupson] def encode(value: A, out: JsonWriter, sort: Boolean): Unit
  private[tupson] def default: Option[A] = None
  private[tupson] def shouldWriteField(value: A): Boolean = true

  private def mapped[B](read: (A, DecodeContext) => B, write: B => A): JsonRW2[B] =
    new JsonRW2[B] {
      private[tupson] def decode(in: JsonReader, ctx: DecodeContext): B = {
        val before = ctx.errorCount
        val value = JsonRW2.this.decode(in, ctx)
        if ctx.errorCount == before then read(value, ctx)
        else null.asInstanceOf[B]
      }

      private[tupson] def encode(value: B, out: JsonWriter, sort: Boolean): Unit =
        JsonRW2.this.encode(write(value), out, sort)
    }
}

object JsonRW2 {
  def apply[A](using rw: JsonRW2[A]): JsonRW2[A] = rw

  inline def derived[A](using mirror: Mirror.Of[A]): JsonRW2[A] =
    inline mirror match {
      case _: Mirror.ProductOf[A] => JsonRW2Product.derived[A]
      case sum: Mirror.SumOf[A]   => JsonRW2Sum.derived[A](sum)
    }

  inline given [A: IsUnion]: JsonRW2[A] = JsonRW2Union.derived[A]

  inline given [A <: String | Char | Boolean | Int | Long | Float | Double](using ValueOf[A]): JsonRW2[A] =
    JsonRW2Literal.derived[A]

  inline given [A <: AnyNamedTuple](using A <:< AnyNamedTuple): JsonRW2[A] =
    JsonRW2NamedTuple.derived[A]

  extension (json: String)
    def parseJson[A](using rw: JsonRW2[A]): A = rw.reader.read(json)

  extension (json: Array[Byte])
    def parseJson[A](using rw: JsonRW2[A]): A = rw.reader.read(json)

  extension [A](value: A)
    def toJson(spaces: Int = 0, sort: Boolean = false)(using rw: JsonRW2[A]): String =
      val writer = if sort then rw.sortedWriter else rw.unsortedWriter
      writer.render(value, spaces)

    /** Writes UTF-8 JSON and flushes the stream, leaving it open. */
    def writeJson(out: OutputStream, spaces: Int = 0, sort: Boolean = false)(using rw: JsonRW2[A]): Unit =
      val writer = if sort then rw.sortedWriter else rw.unsortedWriter
      writer.write(value, out, spaces)

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

  given JsonRW2[UUID] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): UUID =
      readToken(in, ctx, "UUID", null: UUID)(_ == '"')(UUID.fromString(in.readString(null)))

    private[tupson] def encode(value: UUID, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  given JsonRW2[URI] = stringValue("URI")(new URI(_))

  given JsonRW2[Instant] = stringValue("Instant")(Instant.parse)
  given JsonRW2[Duration] = stringValue("Duration")(Duration.parse)
  given JsonRW2[Period] = stringValue("Period")(Period.parse)
  given JsonRW2[LocalDate] = stringValue("LocalDate")(LocalDate.parse)
  given JsonRW2[LocalTime] = stringValue("LocalTime")(LocalTime.parse)
  given JsonRW2[LocalDateTime] = stringValue("LocalDateTime")(LocalDateTime.parse)
  given JsonRW2[OffsetDateTime] = stringValue("OffsetDateTime")(OffsetDateTime.parse)
  given JsonRW2[OffsetTime] = stringValue("OffsetTime")(OffsetTime.parse)
  given JsonRW2[ZonedDateTime] = stringValue("ZonedDateTime")(ZonedDateTime.parse)
  given JsonRW2[ZoneId] = stringValue("ZoneId")(ZoneId.of)
  given JsonRW2[ZoneOffset] = stringValue("ZoneOffset")(ZoneOffset.of)

  given JsonRW2[BigDecimal] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): BigDecimal =
      readToken(in, ctx, "BigDecimal", null: BigDecimal)(isNumber)(readExactDecimal(in))

    private[tupson] def encode(value: BigDecimal, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(scala.math.BigDecimal(value))
  }

  given JsonRW2[BigInteger] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): BigInteger =
      readToken(in, ctx, "BigInteger", null: BigInteger)(isNumber)(readBigInteger(in))

    private[tupson] def encode(value: BigInteger, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(BigInt(value))
  }

  given [A](using element: JsonRW2[A]): JsonRW2[Option[A]] with {
    override private[tupson] def default: Option[Option[A]] = Some(None)

    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Option[A] = {
      if in.isNextToken('n') then in.readNullOrError(None, "expected null")
      else {
        in.rollbackToken()
        val before = ctx.errorCount
        val value = element.decode(in, ctx)
        if ctx.errorCount == before then Option(value) else None
      }
    }

    private[tupson] def encode(value: Option[A], out: JsonWriter, sort: Boolean): Unit =
      value match {
        case Some(item) => element.encode(item, out, sort)
        case None       => out.writeNull()
      }
  }

  given [A](using element: JsonRW2[A]): JsonRW2[List[A]] with {
    override private[tupson] def default: Option[List[A]] = Some(Nil)

    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): List[A] =
      readArray(in, ctx, element, "List", List.empty[A])(List.newBuilder[A])

    private[tupson] def encode(value: List[A], out: JsonWriter, sort: Boolean): Unit = {
      out.writeArrayStart()
      var remaining = value
      while remaining.nonEmpty do {
        element.encode(remaining.head, out, sort)
        remaining = remaining.tail
      }
      out.writeArrayEnd()
    }
  }

  given [A](using element: JsonRW2[A]): JsonRW2[Seq[A]] with {
    override private[tupson] def default: Option[Seq[A]] = Some(Seq.empty)

    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Seq[A] =
      readArray(in, ctx, element, "Seq", Seq.empty[A])(Seq.newBuilder[A])

    private[tupson] def encode(value: Seq[A], out: JsonWriter, sort: Boolean): Unit =
      TupsonWriter.writeArray(value.iterator, out, sort, element)
  }

  given [A](using element: JsonRW2[A]): JsonRW2[Set[A]] with {
    override private[tupson] def default: Option[Set[A]] = Some(Set.empty)

    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Set[A] =
      readArray(in, ctx, element, "Set", Set.empty[A])(Set.newBuilder[A])

    private[tupson] def encode(value: Set[A], out: JsonWriter, sort: Boolean): Unit =
      TupsonWriter.writeArray(value.iterator, out, sort, element)
  }

  given [A: ClassTag](using element: JsonRW2[A]): JsonRW2[Array[A]] with {
    override private[tupson] def default: Option[Array[A]] = Some(Array.empty)

    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Array[A] =
      readArray(in, ctx, element, "Array", Array.empty[A])(Array.newBuilder[A])

    private[tupson] def encode(value: Array[A], out: JsonWriter, sort: Boolean): Unit = {
      out.writeArrayStart()
      var index = 0
      while index < value.length do {
        element.encode(value(index), out, sort)
        index = index + 1
      }
      out.writeArrayEnd()
    }
  }

  given [A](using element: JsonRW2[A]): JsonRW2[Map[String, A]] with {
    override private[tupson] def default: Option[Map[String, A]] = Some(Map.empty)

    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Map[String, A] =
      readToken(in, ctx, "Map", Map.empty[String, A])(_ == '{') {
        in.nextToken()
        if in.isNextToken('}') then Map.empty
        else {
          in.rollbackToken()
          val entries = Map.newBuilder[String, A]
          val keys = HashSet.empty[String]
          val before = ctx.errorCount
          var more = true
          while more do {
            val key = in.readKeyAsString()
            if !keys.add(key) then ctx.duplicateKey(key)
            if ctx.errorCount == before then {
              val value = ctx.withField(key)(element.decode(in, ctx))
              if ctx.errorCount == before then entries.addOne((key, value))
            } else skipValue(in)
            more = in.isNextToken(',')
          }
          if !in.isCurrentToken('}') then in.objectEndOrCommaError()
          if ctx.errorCount == before then entries.result() else Map.empty
        }
      }

    private[tupson] def encode(value: Map[String, A], out: JsonWriter, sort: Boolean): Unit = {
      out.writeObjectStart()
      if sort then {
        val keys = value.keysIterator.toArray.sorted
        keys.foreach { key =>
          out.writeKey(key)
          element.encode(value(key), out, sort)
        }
      } else {
        val entries = value.iterator
        while entries.hasNext do {
          val (key, entry) = entries.next()
          out.writeKey(key)
          element.encode(entry, out, sort)
        }
      }
      out.writeObjectEnd()
    }
  }

  private def stringValue[A](expected: String)(parse: String => A): JsonRW2[A] =
    new JsonRW2[A] {
      private[tupson] def decode(in: JsonReader, ctx: DecodeContext): A =
        readToken(in, ctx, expected, null.asInstanceOf[A])(_ == '"')(parse(in.readString(null)))

      private[tupson] def encode(value: A, out: JsonWriter, sort: Boolean): Unit =
        out.writeVal(value.toString)
    }
}
