package ba.sake.tupson

package seal {
  sealed trait SealedBase derives JsonRW
  case class Sealed1Case(str: String, integer: Int) extends SealedBase
  case class Sealed2Case(str: String) extends SealedBase
  case object Sealed3 extends SealedBase
}
