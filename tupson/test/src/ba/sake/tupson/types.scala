package ba.sake.tupson

import scala.annotation.StaticAnnotation

case class CaseClass1(str: String, integer: Int) derives JsonRW
case class CaseClass2(bla: String, c1: CaseClass1) derives JsonRW

case class CaseClassOpt(str: Option[String], seq: Seq[String], map: Map[String, String]) derives JsonRW
case class CaseClassDefault(
    // parsed as Seq.empty IF THEY KEY IS MISSING (not failing)
    lst: Seq[String] = Seq.empty,

    // parsed as Some("default") IF THEY KEY IS MISSING (not failing)
    str: Option[String] = Some("default")
) derives JsonRW

package rec {
  case class Node(children: Seq[Node]) derives JsonRW
}

package weird_named {
  case class WeirdNamed(`weird named key`: Int) derives JsonRW
}
