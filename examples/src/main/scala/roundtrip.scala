import ba.sake.tupson.{*, given}

@main def roundtrip: Unit = {
  val x = RoundtripData(true, 5, 3.14, "xyz", Seq("a", "b"))

  val json = x.toJson
  println(json)

  val xAgain = json.parseJson[RoundtripData]
  println(xAgain)

  println(s"Both same: ${x == xAgain}")
}

case class RoundtripData(
    bln: Boolean,
    int: Int,
    dbl: Double,
    str: String,
    list: Seq[String]
) derives JsonRW
