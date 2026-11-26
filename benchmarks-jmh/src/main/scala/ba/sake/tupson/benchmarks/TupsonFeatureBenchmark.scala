package ba.sake.tupson.benchmarks

import java.util.concurrent.TimeUnit
import ba.sake.tupson.{Json, JsonRW, parseJson, toJson}
import org.openjdk.jmh.annotations.*

private object BenchmarkData {
  case class Location(city: String, zip: Int) derives JsonRW
  case class Account(
      id: Int,
      name: String,
      location: Location,
      scores: List[Int],
      attributes: Map[String, Boolean],
      active: Boolean = true,
      nickname: Option[String] = None
  ) derives JsonRW
  case class Batch(items: List[Account]) derives JsonRW

  sealed trait Event derives JsonRW
  case class Updated(account: Account) extends Event
  case class Deleted(id: Int) extends Event

  type Row = (name: String, count: Int)

  val account: Account =
    Account(1, "Amira", Location("Sarajevo", 71000), List(1, -2, 3), Map("pinned" -> false))

  def batch(size: Int): Batch =
    Batch(List.tabulate(size)(id => account.copy(id = id)))

  def taggedInput(tagFirst: Boolean): String = {
    val json = account.toJson(spaces = 0, sort = false)
    val updated = if tagFirst then s"""{"@type":"Updated","account":$json}"""
    else s"""{"account":$json,"@type":"Updated"}"""
    val deleted = if tagFirst then """{"@type":"Deleted","id":1}"""
    else """{"id":1,"@type":"Deleted"}"""
    List.fill(50)(List(updated, deleted)).flatten.mkString("[", ",", "]")
  }
}

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
class TupsonFeatureBenchmark {
  import BenchmarkData.*

  @Param(Array("small", "nested", "large", "escaped", "numbers", "tag-first", "tag-last", "union", "named-tuple", "dynamic"))
  var scenario: String = ""

  private var read: () => Any = null
  private var write: () => String = null

  @Setup
  def setup(): Unit = scenario match {
    case "small" => prepare(account.location)
    case "nested" =>
      val input = """{"id":1,"name":"Amira","location":{"city":"Sarajevo","zip":71000},"scores":[1,-2,3],"attributes":{"pinned":false}}"""
      prepare(account, Some(input))
    case "large" => prepare(batch(10000))
    case "escaped" =>
      val text = "quote \" tab\t slash\\ newline\n ž 😀" * 200
      prepare(account.copy(name = text))
    case "numbers" => prepare(List.tabulate(10000)(index => -765432L * index))
    case "tag-first" | "tag-last" =>
      val events: List[Event] = List.fill(50)(List(Updated(account), Deleted(1))).flatten
      prepare(events, Some(taggedInput(tagFirst = scenario == "tag-first")))
    case "union" =>
      val values = List.tabulate[Int | String](128) { index =>
        if index % 2 == 0 then index else s"id-$index"
      }
      prepare(values)
    case "named-tuple" =>
      val value: Row = (name = "Amira", count = 42)
      prepare(value)
    case "dynamic" =>
      val input = """{"status":true,"label":"ž 😀","values":[null,5.00e+02,{"extra":"data"}]}"""
      prepare(input.parseJson[Json], Some(input))
    case other => throw new IllegalArgumentException(s"Unknown benchmark scenario '$other'")
  }

  @Benchmark def decode(): Any = read()
  @Benchmark def encode(): String = write()

  private def prepare[A: JsonRW](value: A, input: Option[String] = None): Unit = {
    val json = input.getOrElse(value.toJson())
    read = () => json.parseJson[A]
    write = () => value.toJson()

    require(read() == value, s"Mismatched decoded value for '$scenario'")
    require(write().parseJson[A] == value, s"Mismatched round-trip value for '$scenario'")
  }
}
