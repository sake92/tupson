package ba.sake.tupson

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException, InputStream}
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
    assertEquals(new ByteArrayInputStream(stream.toByteArray).parseJson[Payload], value)
  }

  test("read UTF-8 from an input stream and leave it open") {
    val input = new RecordingInputStream("""{"items":[{"name":"č😀","id":1}],"label":"test"}""")
    assertEquals(input.parseJson[Payload], Payload(List(Item("č😀", 1)), "test"))
    assert(!input.closed)
  }

  test("preserve validation errors when reading from a stream") {
    val input = new RecordingInputStream("""{"items":[{"name":"test","id":false}],"label":"test"}""")
    val error = intercept[ParsingException](input.parseJson[Payload])
    assertEquals(error.errors, Seq(ParseError("$.items[0].id", "should be Int but it is Boolean", Some("false"))))
    assert(!input.closed)
  }

  test("reject truncated input and trailing JSON") {
    for json <- Seq("[1", "[1] [2]") do {
      val input = new RecordingInputStream(json)
      interceptMessage[TupsonException]("JSON parsing exception")(input.parseJson[List[Int]])
      assert(!input.closed)
    }
  }

  test("propagate stream read failures") {
    val failure = new IOException("read failed")
    val input = new InputStream {
      override def read(): Int = throw failure
    }
    assert(intercept[IOException](input.parseJson[Int]) eq failure)
  }

  private final class RecordingInputStream(json: String) extends ByteArrayInputStream(json.getBytes(UTF_8)) {
    var closed = false

    override def close(): Unit = closed = true
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
