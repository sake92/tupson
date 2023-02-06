package ba.sake.tupson

package enums {

  enum Semaphore derives JsonRW:
    case Red, Yellow, Green

  enum Color(val rgb: Int):
    case Red extends Color(0xff0000)
    case Green extends Color(0x00ff00)
    case Blue extends Color(0x0000ff)

  enum Enum1 derives JsonRW:
    case Enum1Case(str: String, integer: Option[Int])
    case Enum2Case()
    case `eNum CaseD`

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
