package ba.sake.tupson.benchmarks

import java.io.OutputStream
import java.util.concurrent.TimeUnit
import ba.sake.tupson.{JsonRW, toJson, writeJson}
import org.openjdk.jmh.annotations.*

private object Arena {
  case class Rating(score: Int, count: Int) derives JsonRW
  case class Item(
      id: Int,
      name: String,
      category: String,
      price: Long,
      quantity: Long,
      active: Boolean,
      tags: Seq[String],
      rating: Rating,
      total: Long = 0
  ) derives JsonRW
  case class Payload(items: Seq[Item], count: Int) derives JsonRW

  def payload(size: Int): Payload = {
    val items = (0 until size).map { i =>
      Item(i, s"Item $i", "electronics", 1000L + i, i % 7, i % 2 == 0, Seq("fast", "new"), Rating(i % 5, i * 3), i * 2L)
    }
    Payload(items, size)
  }
}

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
class TupsonArenaBenchmark {
  @Param(Array("1", "50", "1000"))
  var items: Int = 0

  private var payload: Arena.Payload = scala.compiletime.uninitialized
  private val out = new CountingOutputStream

  @Setup
  def prepare(): Unit = {
    payload = Arena.payload(items)
    require(writeStream() == toJsonString(), "Stream and string lengths differ for the ASCII payload")
  }

  @Benchmark def writeStream(): Long = {
    out.count = 0
    payload.writeJson(out)
    out.count
  }

  @Benchmark def toJsonString(): Int = payload.toJson().length

  private final class CountingOutputStream extends OutputStream {
    var count: Long = 0

    override def write(value: Int): Unit = count += 1
    override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = count += length
  }
}
