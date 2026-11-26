package ba.sake.tupson

import java.io.OutputStream
import com.github.plokhotnyuk.jsoniter_scala.core.*

private[tupson] final class TupsonWriter[A](codec: JsonRW[A], sort: Boolean) extends JsonValueCodec[A] {
  def render(value: A, spaces: Int): String =
    writeToString(value, config(spaces))(using this)

  def write(value: A, out: OutputStream, spaces: Int): Unit = {
    writeToStream(value, out, config(spaces))(using this)
    out.flush()
  }

  def nullValue: A = null.asInstanceOf[A]

  def decodeValue(in: JsonReader, default: A): A =
    throw new UnsupportedOperationException("write adapter")

  def encodeValue(value: A, out: JsonWriter): Unit = codec.encode(value, out, sort)

  private def config(spaces: Int): WriterConfig =
    if spaces <= 0 then WriterConfig else WriterConfig.withIndentionStep(spaces)
}

private[tupson] object TupsonWriter {
  def writeArray[A](values: Iterator[A], out: JsonWriter, sort: Boolean, element: JsonRW[A]): Unit = {
    out.writeArrayStart()
    while values.hasNext do element.encode(values.next(), out, sort)
    out.writeArrayEnd()
  }
}
