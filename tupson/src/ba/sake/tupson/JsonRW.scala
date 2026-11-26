package ba.sake.tupson

import java.math.{BigDecimal, BigInteger}
import java.net.URI
import java.time.{Duration, Instant, LocalDate, LocalDateTime, LocalTime, Period}
import java.time.{DayOfWeek, Month, MonthDay, Year, YearMonth}
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
abstract class JsonRW[A] private[tupson] () {
  private val reader = new TupsonReader(this)
  private val sortedWriter = new TupsonWriter(this, sort = true)
  private val unsortedWriter = new TupsonWriter(this, sort = false)

  final def bimap[B](read: A => B)(write: B => A): JsonRW[B] =
    mapped((value, _) => read(value), write)

  final def emap[B](read: A => Either[String, B])(write: B => A): JsonRW[B] =
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

  private[tupson] def readJson(json: String): A = reader.read(json)
  private[tupson] def readJson(json: Array[Byte]): A = reader.read(json)

  private[tupson] def renderJson(value: A, spaces: Int, sort: Boolean): String = {
    val writer = if sort then sortedWriter else unsortedWriter
    writer.render(value, spaces)
  }

  private[tupson] def writeTo(value: A, out: java.io.OutputStream, spaces: Int, sort: Boolean): Unit = {
    val writer = if sort then sortedWriter else unsortedWriter
    writer.write(value, out, spaces)
  }

  private def mapped[B](read: (A, DecodeContext) => B, write: B => A): JsonRW[B] =
    new JsonRW[B] {
      private[tupson] def decode(in: JsonReader, ctx: DecodeContext): B = {
        val before = ctx.errorCount
        val value = JsonRW.this.decode(in, ctx)
        if ctx.errorCount == before then read(value, ctx)
        else null.asInstanceOf[B]
      }

      private[tupson] def encode(value: B, out: JsonWriter, sort: Boolean): Unit =
        JsonRW.this.encode(write(value), out, sort)
    }
}

object JsonRW extends LowPriorityJsonRWInstances {
  def apply[A](using rw: JsonRW[A]): JsonRW[A] = rw

  inline def derived[A](using mirror: Mirror.Of[A]): JsonRW[A] =
    inline mirror match {
      case _: Mirror.ProductOf[A] => JsonRWProduct.derived[A]
      case sum: Mirror.SumOf[A]   => JsonRWSum.derived[A](sum)
    }

  inline given [A: IsUnion]: JsonRW[A] = JsonRWUnion.derived[A]

  given JsonRW[String] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): String =
      readToken(in, ctx, "String", null: String)(_ == '"')(in.readString(null))

    private[tupson] def encode(value: String, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  given JsonRW[Char] with {
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

  given JsonRW[Boolean] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Boolean =
      readToken(in, ctx, "Boolean", false)(b => b == 't' || b == 'f')(in.readBoolean())

    private[tupson] def encode(value: Boolean, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  given JsonRW[Int] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Int =
      readToken(in, ctx, "Int", 0)(isNumber)(in.readInt())

    private[tupson] def encode(value: Int, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  given JsonRW[Long] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Long =
      readToken(in, ctx, "Long", 0L)(isNumber)(in.readLong())

    private[tupson] def encode(value: Long, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  given JsonRW[Float] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Float =
      readToken(in, ctx, "Float", 0f)(isNumber)(in.readFloat())

    private[tupson] def encode(value: Float, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value.toDouble)
  }

  given JsonRW[Double] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Double =
      readToken(in, ctx, "Double", 0d)(isNumber)(in.readDouble())

    private[tupson] def encode(value: Double, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  given JsonRW[UUID] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): UUID =
      readToken(in, ctx, "UUID", null: UUID)(_ == '"')(UUID.fromString(in.readString(null)))

    private[tupson] def encode(value: UUID, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(value)
  }

  given JsonRW[URI] = stringValue("URI")(new URI(_))

  given JsonRW[Instant] = stringValue("Instant")(Instant.parse)
  given JsonRW[Duration] = stringValue("Duration")(Duration.parse)
  given JsonRW[Period] = stringValue("Period")(Period.parse)
  given JsonRW[LocalDate] = stringValue("LocalDate")(LocalDate.parse)
  given JsonRW[LocalTime] = stringValue("LocalTime")(LocalTime.parse)
  given JsonRW[LocalDateTime] = stringValue("LocalDateTime")(LocalDateTime.parse)
  given JsonRW[OffsetDateTime] = stringValue("OffsetDateTime")(OffsetDateTime.parse)
  given JsonRW[OffsetTime] = stringValue("OffsetTime")(OffsetTime.parse)
  given JsonRW[ZonedDateTime] = stringValue("ZonedDateTime")(ZonedDateTime.parse)
  given JsonRW[ZoneId] = stringValue("ZoneId")(ZoneId.of)
  given JsonRW[ZoneOffset] = stringValue("ZoneOffset")(ZoneOffset.of)
  given JsonRW[YearMonth] = stringValue("YearMonth")(YearMonth.parse)
  given JsonRW[MonthDay] = stringValue("MonthDay")(MonthDay.parse)
  given JsonRW[Year] = stringValue("Year")(Year.parse)
  given JsonRW[Month] = stringValue("Month")(Month.valueOf)
  given JsonRW[DayOfWeek] = stringValue("DayOfWeek")(DayOfWeek.valueOf)

  given JsonRW[BigDecimal] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): BigDecimal =
      readToken(in, ctx, "BigDecimal", null: BigDecimal)(isNumber)(readExactDecimal(in))

    private[tupson] def encode(value: BigDecimal, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(scala.math.BigDecimal(value))
  }

  given JsonRW[BigInteger] with {
    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): BigInteger =
      readToken(in, ctx, "BigInteger", null: BigInteger)(isNumber)(readBigInteger(in))

    private[tupson] def encode(value: BigInteger, out: JsonWriter, sort: Boolean): Unit =
      out.writeVal(BigInt(value))
  }

  given [A](using element: JsonRW[A]): JsonRW[Option[A]] with {
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

  given [A](using element: JsonRW[A]): JsonRW[List[A]] with {
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

  given [A](using element: JsonRW[A]): JsonRW[Seq[A]] with {
    override private[tupson] def default: Option[Seq[A]] = Some(Seq.empty)

    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Seq[A] =
      readArray(in, ctx, element, "Seq", Seq.empty[A])(Seq.newBuilder[A])

    private[tupson] def encode(value: Seq[A], out: JsonWriter, sort: Boolean): Unit =
      TupsonWriter.writeArray(value.iterator, out, sort, element)
  }

  given [A](using element: JsonRW[A]): JsonRW[Set[A]] with {
    override private[tupson] def default: Option[Set[A]] = Some(Set.empty)

    private[tupson] def decode(in: JsonReader, ctx: DecodeContext): Set[A] =
      readArray(in, ctx, element, "Set", Set.empty[A])(Set.newBuilder[A])

    private[tupson] def encode(value: Set[A], out: JsonWriter, sort: Boolean): Unit =
      TupsonWriter.writeArray(value.iterator, out, sort, element)
  }

  given [A: ClassTag](using element: JsonRW[A]): JsonRW[Array[A]] with {
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

  given [A](using element: JsonRW[A]): JsonRW[Map[String, A]] with {
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

  private[tupson] def stringValue[A](expected: String)(parse: String => A): JsonRW[A] =
    stringValue(expected, parse, _.toString)

  private[tupson] def stringValue[A](expected: String, parse: String => A, render: A => String): JsonRW[A] =
    new JsonRW[A] {
      private[tupson] def decode(in: JsonReader, ctx: DecodeContext): A =
        readToken(in, ctx, expected, null.asInstanceOf[A])(_ == '"')(parse(in.readString(null)))

      private[tupson] def encode(value: A, out: JsonWriter, sort: Boolean): Unit =
        out.writeVal(render(value))
    }
}

private[tupson] trait LowPriorityJsonRWInstances {
  inline given [A <: String | Char | Boolean | Int | Long | Float | Double](using ValueOf[A]): JsonRW[A] =
    JsonRWLiteral.derived[A]

  inline given [A <: AnyNamedTuple](using A <:< AnyNamedTuple): JsonRW[A] =
    JsonRWNamedTuple.derived[A]
}
