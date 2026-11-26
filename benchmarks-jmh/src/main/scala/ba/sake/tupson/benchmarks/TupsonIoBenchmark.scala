package ba.sake.tupson.benchmarks

import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.nio.charset.StandardCharsets.UTF_8
import ba.sake.tupson.{ParsingException, parseJson, toJson, writeJson}
import org.openjdk.jmh.annotations.*

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
class TupsonIoBenchmark {
  private val payload = BenchmarkData.batch(1000)
  private val out = new CountingOutputStream
  private val invalid = """{"id":false,"name":12,"location":{"zip":true},"scores":[false,1],"attributes":{}}"""

  @Setup
  def checkOutputs(): Unit = {
    require(stream() == payload.toJson().getBytes(UTF_8).length, "Stream and string lengths differ")
    val paths = decodeInvalid().errors.map(_.path).toSet
    require(paths == Set("$.id", "$.name", "$.location.city", "$.location.zip", "$.scores[0]"))
  }

  @Benchmark def stream(): Long = {
    out.count = 0
    payload.writeJson(out)
    out.count
  }

  @Benchmark def decodeInvalid(): ParsingException =
    try {
      invalid.parseJson[BenchmarkData.Account]
      throw new AssertionError("Expected a parse error")
    } catch { case e: ParsingException => e }

  private final class CountingOutputStream extends OutputStream {
    var count: Long = 0

    override def write(value: Int): Unit = count = count + 1
    override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = count = count + length
  }
}
