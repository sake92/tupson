package ba.sake.tupson

package seal {
  sealed trait SealedBase derives JsonRW
  case class SealedCase1(str: String, integer: Int) extends SealedBase
  case object SealedCase2 extends SealedBase
}

package annotated {

  @discriminator("tip")
  enum Annot1 derives JsonRW:
    case A
    case B(x: String)
}
