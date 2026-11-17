package ba.sake.tupson

import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8

import scala.annotation.switch
import scala.util.Sorting

import org.typelevel.jawn.ast.*

private[tupson] object TupsonRenderer {

  def render(jValue: JValue, spaces: Int, sort: Boolean): String = {
    val normalizedSpaces =
      if spaces <= 0 then 0 else spaces
    if normalizedSpaces == 0 then
      if sort then CanonicalRenderer.render(jValue)
      else FastRenderer.render(jValue)
    else {
      val sb = new StringBuilder
      renderPretty(jValue, sb, 0, normalizedSpaces, sort)
      sb.toString
    }
  }

  def write(jValue: JValue, output: OutputStream, spaces: Int, sort: Boolean): Unit = {
    val normalizedSpaces =
      if spaces <= 0 then 0 else spaces
    if normalizedSpaces == 0 then {
      val writer = new Utf8Output(output)
      writeCompact(jValue, writer, sort)
      writer.flush()
    } else {
      val sb = new StringBuilder
      renderPretty(jValue, sb, 0, normalizedSpaces, sort)
      output.write(sb.toString.getBytes(UTF_8))
    }
  }

  private def writeCompact(jValue: JValue, writer: Utf8Output, sort: Boolean): Unit =
    jValue match {
      case JNull          => writer.writeAscii("null")
      case JTrue          => writer.writeAscii("true")
      case JFalse         => writer.writeAscii("false")
      case LongNum(n)     => writer.writeAscii(n.toString)
      case DoubleNum(n)   => writer.writeAscii(n.toString)
      case DeferNum(s)    => writer.writeAscii(s)
      case DeferLong(s)   => writer.writeAscii(s)
      case JString(s)     => writeString(writer, s, unicode = sort)
      case JArray(values) =>
        writer.writeByte('[')
        var index = 0
        while index < values.length do {
          if index > 0 then writer.writeByte(',')
          writeCompact(values(index), writer, sort)
          index += 1
        }
        writer.writeByte(']')
      case JObject(fields) =>
        writer.writeByte('{')
        if sort then {
          val keys = fields.keys.toArray
          Sorting.quickSort(keys)
          var index = 0
          while index < keys.length do {
            if index > 0 then writer.writeByte(',')
            val key = keys(index)
            writeString(writer, key, unicode = true)
            writer.writeByte(':')
            writeCompact(fields(key), writer, sort)
            index += 1
          }
        } else {
          val entries = fields.iterator
          var first = true
          while entries.hasNext do {
            val (key, value) = entries.next()
            if !first then writer.writeByte(',')
            writeString(writer, key, unicode = false)
            writer.writeByte(':')
            writeCompact(value, writer, sort)
            first = false
          }
        }
        writer.writeByte('}')
    }

  private def writeString(writer: Utf8Output, value: String, unicode: Boolean): Unit = {
    writer.writeByte('"')
    if needsEscaping(value, unicode) then {
      var index = 0
      while index < value.length do {
        (value.charAt(index): @switch) match {
          case '"'  => writer.writeAscii("\\\"")
          case '\\' => writer.writeAscii("\\\\")
          case '\b' => writer.writeAscii("\\b")
          case '\f' => writer.writeAscii("\\f")
          case '\n' => writer.writeAscii("\\n")
          case '\r' => writer.writeAscii("\\r")
          case '\t' => writer.writeAscii("\\t")
          case char =>
            if char < ' ' || (char > '~' && unicode) then writer.writeUnicodeEscape(char)
            else if Character.isHighSurrogate(char) && index + 1 < value.length then {
              val low = value.charAt(index + 1)
              if Character.isLowSurrogate(low) then {
                writer.writeCodePoint(Character.toCodePoint(char, low))
                index += 1
              } else writer.writeByte('?')
            } else if Character.isLowSurrogate(char) then writer.writeByte('?')
            else writer.writeUtf8(char)
        }
        index += 1
      }
    } else {
      writer.writeString(value)
    }
    writer.writeByte('"')
  }

  private def needsEscaping(value: String, unicode: Boolean): Boolean = {
    var index = 0
    var escapingRequired = false
    while index < value.length && !escapingRequired do {
      val char = value.charAt(index)
      escapingRequired = char == '"' || char == '\\' || char < ' ' || (char > '~' && unicode)
      index += 1
    }
    escapingRequired
  }

  private final class Utf8Output(output: OutputStream) {
    private val buffer = new Array[Byte](8 * 1024)
    private var size = 0

    def writeByte(value: Int): Unit = {
      if size == buffer.length then flushBuffer()
      buffer(size) = value.toByte
      size += 1
    }

    def writeAscii(value: String): Unit = {
      var index = 0
      while index < value.length do {
        writeByte(value.charAt(index))
        index += 1
      }
    }

    def writeString(value: String): Unit = {
      var index = 0
      while index < value.length do {
        val char = value.charAt(index)
        if Character.isHighSurrogate(char) && index + 1 < value.length then {
          val low = value.charAt(index + 1)
          if Character.isLowSurrogate(low) then {
            writeCodePoint(Character.toCodePoint(char, low))
            index += 1
          } else writeByte('?')
        } else if Character.isLowSurrogate(char) then writeByte('?')
        else writeUtf8(char)
        index += 1
      }
    }

    def writeUtf8(char: Char): Unit =
      if char <= 0x7f then writeByte(char)
      else if char <= 0x7ff then {
        writeByte(0xc0 | (char >> 6))
        writeByte(0x80 | (char & 0x3f))
      } else {
        writeByte(0xe0 | (char >> 12))
        writeByte(0x80 | ((char >> 6) & 0x3f))
        writeByte(0x80 | (char & 0x3f))
      }

    def writeUnicodeEscape(char: Char): Unit = {
      writeAscii("\\u")
      writeByte(hexDigit(char >> 12))
      writeByte(hexDigit((char >> 8) & 0xf))
      writeByte(hexDigit((char >> 4) & 0xf))
      writeByte(hexDigit(char & 0xf))
    }

    def flush(): Unit = {
      flushBuffer()
      output.flush()
    }

    def writeCodePoint(codePoint: Int): Unit = {
      writeByte(0xf0 | (codePoint >> 18))
      writeByte(0x80 | ((codePoint >> 12) & 0x3f))
      writeByte(0x80 | ((codePoint >> 6) & 0x3f))
      writeByte(0x80 | (codePoint & 0x3f))
    }

    private def flushBuffer(): Unit = {
      if size > 0 then output.write(buffer, 0, size)
      size = 0
    }

    private def hexDigit(value: Int): Int =
      if value < 10 then '0' + value else 'a' + (value - 10)
  }

  private def renderPretty(
      jValue: JValue,
      sb: StringBuilder,
      depth: Int,
      spaces: Int,
      sort: Boolean
  ): Unit = jValue match {
    case JObject(fields) =>
      val orderedFields =
        if sort then fields.toSeq.sortBy(_._1)
        else fields.toSeq
      if orderedFields.isEmpty then sb.append("{}")
      else {
        sb.append("{\n")
        orderedFields.zipWithIndex.foreach { case ((key, value), index) =>
          indent(sb, depth + 1, spaces)
          sb.append(FastRenderer.render(JString(key)))
          sb.append(": ")
          renderPretty(value, sb, depth + 1, spaces, sort)
          if index < orderedFields.size - 1 then sb.append(',')
          sb.append('\n')
        }
        indent(sb, depth, spaces)
        sb.append('}')
      }
    case JArray(values) =>
      if values.isEmpty then sb.append("[]")
      else {
        sb.append("[\n")
        values.zipWithIndex.foreach { case (value, index) =>
          indent(sb, depth + 1, spaces)
          renderPretty(value, sb, depth + 1, spaces, sort)
          if index < values.length - 1 then sb.append(',')
          sb.append('\n')
        }
        indent(sb, depth, spaces)
        sb.append(']')
      }
    case other =>
      sb.append(FastRenderer.render(other))
  }

  private def indent(sb: StringBuilder, depth: Int, spaces: Int): Unit =
    sb.append(" " * (depth * spaces))
}
