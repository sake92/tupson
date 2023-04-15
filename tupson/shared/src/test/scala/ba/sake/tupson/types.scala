package ba.sake.tupson

case class CaseClass1(str: String, integer: Int) derives JsonRW
case class CaseClass2(bla: String, c1: CaseClass1) derives JsonRW

case class ValidCaseClass1(str: String) derives JsonRW {
  import ba.sake.validation.*
  validate(
    check(str).is(_.length > 3, "must be > 3").is(_.length < 10, "must be < 10")
  )
}
case class ValidCaseClass2(str: String, integer: Int, list: List[ValidCaseClass1]) derives JsonRW {
  import ba.sake.validation.*
  validate(
    check(str).is(_.length > 3, "must be > 3").is(_.length < 10, "must be < 10"),
    check(integer).is(_ > 0, "must be positive")
  )
}

case class CaseClassOpt(str: Option[String]) derives JsonRW
case class CaseClassDefault(
    // parsed as List.empty IF THEY KEY IS MISSING (not failing)
    lst: List[String] = List.empty,

    // parsed as Some("default") IF THEY KEY IS MISSING (not failing)
    str: Option[String] = Some("default")
) derives JsonRW

package rec {
  case class Node(children: List[Node]) derives JsonRW
}

package weird_named {
  case class WeirdNamed(`weird named key`: Int) derives JsonRW
}
