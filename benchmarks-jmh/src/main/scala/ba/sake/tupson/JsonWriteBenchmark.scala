package ba.sake.tupson

import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import org.typelevel.jawn.ast.{FastRenderer, JValue}

case class BenchmarkRating(score: Int, count: Int) derives JsonRW
case class BenchmarkItem(
    id: Int,
    name: String,
    category: String,
    price: Long,
    quantity: Long,
    active: Boolean,
    tags: Seq[String],
    rating: BenchmarkRating,
    total: Long
) derives JsonRW
case class BenchmarkPayload(items: Seq[BenchmarkItem], count: Int) derives JsonRW

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
class JsonWriteBenchmark {
  @Param(Array("5", "25", "50"))
  var itemCount: Int = 0

  private var payload: BenchmarkPayload = null
  private var payloadRw: JsonRW[BenchmarkPayload] = null
  private var payloadAst: JValue = null
  private var escapedPayloadAst: JValue = null

  @Setup
  def setup(): Unit = {
    payload = BenchmarkPayload(items = benchmarkItems(itemCount), count = itemCount)
    payloadRw = summon[JsonRW[BenchmarkPayload]]
    payloadAst = payloadRw.write(payload)
    val escapedPayload = BenchmarkPayload(items = benchmarkItems(itemCount, escaped = true), count = itemCount)
    escapedPayloadAst = payloadRw.write(escapedPayload)
  }

  @Benchmark
  def buildAst(blackhole: Blackhole): Unit =
    blackhole.consume(payloadRw.write(payload))

  @Benchmark
  def renderPrebuiltAst(blackhole: Blackhole): Unit =
    blackhole.consume(TupsonRenderer.render(payloadAst, spaces = 0, sort = false))

  @Benchmark
  def renderPrebuiltAstJawn(blackhole: Blackhole): Unit =
    blackhole.consume(FastRenderer.render(payloadAst))

  @Benchmark
  def renderEscapedPrebuiltAst(blackhole: Blackhole): Unit =
    blackhole.consume(TupsonRenderer.render(escapedPayloadAst, spaces = 0, sort = false))

  @Benchmark
  def renderEscapedPrebuiltAstJawn(blackhole: Blackhole): Unit =
    blackhole.consume(FastRenderer.render(escapedPayloadAst))

  @Benchmark
  def renderJson(blackhole: Blackhole): Unit =
    blackhole.consume(payload.toJson(spaces = 0, sort = false))

  @Benchmark
  def renderJsonUtf8(blackhole: Blackhole): Unit = {
    val json = payload.toJson(spaces = 0, sort = false)
    blackhole.consume(json.getBytes(UTF_8))
  }

  @Benchmark
  def writeJson(blackhole: Blackhole): Unit = {
    val output = new CountingOutputStream
    payload.writeJsonTo(output, spaces = 0, sort = false)
    blackhole.consume(output.count)
  }

  @Benchmark
  def writeJsonGzip(blackhole: Blackhole): Unit = {
    val output = new CountingOutputStream
    val gzip = new GZIPOutputStream(output, 8 * 1024)
    payload.writeJsonTo(gzip, spaces = 0, sort = false)
    gzip.finish()
    blackhole.consume(output.count)
  }

  @Benchmark
  def renderJsonGzip(blackhole: Blackhole): Unit = {
    val output = new CountingOutputStream
    val gzip = new GZIPOutputStream(output, 8 * 1024)
    gzip.write(payload.toJson(spaces = 0, sort = false).getBytes(UTF_8))
    gzip.finish()
    blackhole.consume(output.count)
  }

  private def benchmarkItems(count: Int, escaped: Boolean = false): Seq[BenchmarkItem] =
    (1 to count).map { id =>
      val name = if escaped then s"Product \"$id\"\\line\n" else s"Product $id"
      val category = if escaped then s"category\t${id % 5}" else s"category-${id % 5}"
      val tags =
        if escaped then Seq("bench\\mark", s"tag \"${id % 7}\"") else Seq("benchmark", s"tag-${id % 7}", "json")
      BenchmarkItem(
        id = id,
        name = name,
        category = category,
        price = 100L + id,
        quantity = 10L + id,
        active = id % 2 == 0,
        tags = tags,
        rating = BenchmarkRating(score = 40 + id % 10, count = 100 + id),
        total = (100L + id) * (10L + id)
      )
    }

  private final class CountingOutputStream extends OutputStream {
    var count: Long = 0
    override def write(value: Int): Unit = count += 1
    override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = count += length
  }
}
