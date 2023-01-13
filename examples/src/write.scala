import ba.sake.tupson.*

@main def write: Unit = {
  var x = WriteData(true, 5, 3.14, "xyz", Seq("a", "b"))
  println(s"Writing data: $x")
  println(x.toJson)
}

case class WriteData(
    bln: Boolean,
    int: Int,
    dbl: Double,
    str: String,
    list: Seq[String]
) derives JsonRW
