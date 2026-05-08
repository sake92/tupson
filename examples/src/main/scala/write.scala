import ba.sake.tupson.{*, given}

@main def write: Unit = {
  val x = WriteData(true, 5, 3.14, "xyz", Seq("a", "b"))
  println(s"Writing data: $x")
  println(x.toJson)
  println(x.toJson(pretty = true))
  println(x.toJson(sort = true))
}

case class WriteData(
    bln: Boolean,
    int: Int,
    dbl: Double,
    str: String,
    list: Seq[String]
) derives JsonRW
