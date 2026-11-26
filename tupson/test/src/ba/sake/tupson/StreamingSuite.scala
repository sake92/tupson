package ba.sake.tupson

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8

class StreamingSuite extends munit.FunSuite {
  import StreamingSuite.*

  test("write the same UTF-8 as toJson and flush without closing") {
    val value = Payload(List(Item("č😀\"\\", 1)), "test")
    for {
      spaces <- Seq(-1, 0, 2, 4)
      sort <- Seq(false, true)
    } {
      val stream = new RecordingStream
      value.writeJson(stream, spaces, sort)
      val output = new String(stream.toByteArray, UTF_8)
      assertEquals(output, value.toJson(spaces, sort))
      assert(stream.flushed)
      assert(!stream.closed)
    }
  }

  test("write a large payload across multiple stream writes") {
    val value = Payload(List.tabulate(10000)(index => Item("test", index)), "large")
    val stream = new RecordingStream
    value.writeJson(stream)
    assert(stream.writes > 1)
    assertEquals(stream.toByteArray.parseJson[Payload], value)
  }

  private final class RecordingStream extends ByteArrayOutputStream {
    var flushed = false
    var closed = false
    var writes = 0

    override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = {
      writes = writes + 1
      super.write(bytes, offset, length)
    }

    override def flush(): Unit = flushed = true
    override def close(): Unit = closed = true
  }
}

object StreamingSuite {
  case class Item(name: String, id: Int) derives JsonRW
  case class Payload(items: List[Item], label: String) derives JsonRW
}
