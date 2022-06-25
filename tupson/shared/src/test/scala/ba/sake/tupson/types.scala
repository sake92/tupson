package ba.sake.tupson

case class CaseClass1(str: String, integer: Int) derives JsonRW
case class CaseClass2(bla: String, c1: CaseClass1) derives JsonRW

package seal {
  sealed trait Sealed1 derives JsonRW
  case class Sealed1Case(str: String, integer: Int) extends Sealed1
  case class Sealed2Case(str: String) extends Sealed1
  case object Sealed3 extends Sealed1
}

package enums {
  enum Enum1 derives JsonRW:
    case Enum1Case(str: String, integer: Option[Int])
    case Enum2Case()
    case Enum3Case

  enum Abc derives JsonRW:
    case Abc1, Abc2
}

package rec {
  case class Node(children: List[Node]) derives JsonRW
}

package rename {
  case class Renamed(@named("newName") x: Int) derives JsonRW
  case class DuplicateName(x: Int, @named("x") y: String) derives JsonRW
}
