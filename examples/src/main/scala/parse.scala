
//> using lib "ba.sake::tupson:0.5.0"

import ba.sake.tupson.*

@main def parse: Unit = {
  var x = """{
    "str":"xyz", "bln":true, "list":["a","b"], "int":5, "dbl":3.14
  }"""
  println(s"Parsing data: $x")
  println(x.parseJson[ParsedData])
}

case class ParsedData(
    bln: Boolean,
    int: Int,
    dbl: Double,
    str: String,
    list: Seq[String]
) derives JsonRW
