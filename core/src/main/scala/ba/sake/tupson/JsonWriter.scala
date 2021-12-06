package ba.sake.tupson

// https://github.com/lampepfl/dotty-macro-examples/tree/master/macroTypeClassDerivation
// https://www.json.org/json-en.html

trait JsonWriter[T]:
  def write(value: T): String

object JsonWriter extends JsonWriterLowPri:
  extension [T: JsonWriter](value: T) def toJson: String =
    summon[JsonWriter[T]].write(value)

  // instances
  given JsonWriter[String] = str => s"\"$str\""

  given JsonWriter[Boolean] = _.toString

  given JsonWriter[Float] = _.toString

  given JsonWriter[Double] = _.toString

  given JsonWriter[Int] = _.toString

  given JsonWriter[Long] = num =>
    if num > 9007199254740991L then s"\"$num\""
    else if num < -9007199254740991L then s"\"$num\""
    else num.toString

  given seqWriter[T, Sq[T] <: Seq[T]](using JsonWriter[T]): JsonWriter[Sq[T]] =
    seq =>
      val tWriter = summon[JsonWriter[T]]
      val tJsons = seq.map(tWriter.write)
      s"[${tJsons.mkString(",")}]"
  
  given arrWriter[T](using JsonWriter[T]): JsonWriter[Array[T]] =
    arr =>
      val tWriter = summon[JsonWriter[T]]
      val tJsons = arr.map(tWriter.write)
      s"[${tJsons.mkString(",")}]"

  given optWriter[T](using JsonWriter[T]): JsonWriter[Option[T]] =
    arr =>
      val tWriter = summon[JsonWriter[T]]
      arr.map(tWriter.write).getOrElse("null")

  

trait JsonWriterLowPri:
  inline given [T]: JsonWriter[T] = JsonWriterMacro.deriveJsonWriter[T]
