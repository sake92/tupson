package ba.sake.tupson

import com.github.plokhotnyuk.jsoniter_scala.core.*

private[tupson] final class TupsonWriter[A](codec: JsonRW2[A], sort: Boolean) extends JsonValueCodec[A] {
  def render(value: A, spaces: Int): String = {
    val config = if spaces <= 0 then WriterConfig else WriterConfig.withIndentionStep(spaces)
    writeToString(value, config)(using this)
  }

  def nullValue: A = null.asInstanceOf[A]

  def decodeValue(in: JsonReader, default: A): A =
    throw new UnsupportedOperationException("write adapter")

  def encodeValue(value: A, out: JsonWriter): Unit = codec.encode(value, out, sort)
}
