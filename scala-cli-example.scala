//> using lib "ba.sake::tupson:0.2.0"

import ba.sake.tupson.*

@main def example: Unit = {
  var x = RoundtripData(true, 5, 3.14, "xyz", Seq("a", "b"))
  
  val json = x.toJson
  println(json)

  val xAgain = json.parseJson[RoundtripData]
  println(xAgain)

  println(s"before == after: ${x == xAgain}")
}

case class RoundtripData(
    bln: Boolean,
    int: Int,
    dbl: Double,
    str: String,
    list: Seq[String]
) derives JsonRW
