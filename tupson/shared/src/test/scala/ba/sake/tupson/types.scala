package ba.sake.tupson

case class CaseClass1(str: String, integer: Int) derives JsonRW
case class CaseClass2(bla: String, c1: CaseClass1) derives JsonRW

case class CaseClassOpt(str: Option[String]) derives JsonRW
case class CaseClassDefault(
    lst: List[String] =
      List.empty, // it should be parsed as List.empty IF THEY KEY IS MISSING (not failing)
    str: Option[String] = Some(
      "default"
    ) // it should be parsed as Some("default") IF THEY KEY IS MISSING
) derives JsonRW

package seal {
  sealed trait SealedBase derives JsonRW
  case class Sealed1Case(str: String, integer: Int) extends SealedBase
  case class Sealed2Case(str: String) extends SealedBase
  case object Sealed3 extends SealedBase
}

package enums {
  enum Enum1 derives JsonRW:
    case Enum1Case(str: String, integer: Option[Int])
    case Enum2Case()
    case `eNum CaseD`

  enum Abc derives JsonRW:
    case Abc1, Abc2

  object inner {
    object burried {
      enum Inside:
        case Abc
    }
    class instance {
      enum Inside:
        case Def
    }
  }
}

package rec {
  case class Node(children: List[Node]) derives JsonRW
}

package weird_named {
  case class WeirdNamed(`weird named key`: Int) derives JsonRW
}
