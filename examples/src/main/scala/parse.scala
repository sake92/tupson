import ba.sake.tupson.{*, given}
import org.typelevel.jawn.ast.JValue

@main def parse: Unit = {
  val x = """{
    "str":"xyz", "bln":true, "list":["a","b"], "int":5, "dbl":3.14, "dynamic": {"d1":"123", "d2": 555}
  }"""
  println(s"Parsing data: $x")
  println(x.parseJson[ParsedData])
}

case class ParsedData(
    bln: Boolean,
    int: Int,
    dbl: Double,
    str: String,
    list: Seq[String],
    dynamic: JValue
) derives JsonRW
