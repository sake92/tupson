package ba.sake.tupson

import scala.annotation.StaticAnnotation
import org.typelevel.jawn.ast.*

case class CaseClass1(str: String, integer: Int) derives JsonRW
case class CaseClass2(bla: String, c1: CaseClass1) derives JsonRW

case class CaseClassOpt(str: Option[String], seq: Seq[String], map: Map[String, String]) derives JsonRW
case class CaseClassDefault(
    // parsed as Seq.empty IF THE KEY IS MISSING (not failing)
    lst: Seq[String] = Seq.empty,

    // parsed as Some("default") IF THE KEY IS MISSING (not failing)
    str: Option[String] = Some("default")
) derives JsonRW
case class LiteralStringCaseClass(x: "abc") derives JsonRW
case class LiteralIntCaseClass(x: 123) derives JsonRW
case class LiteralBooleanCaseClass(x: true) derives JsonRW
case class LiteralCharCaseClass(x: 'a') derives JsonRW

enum Patch[+T]:
  case Set(value: T)
  case Clear
  case Keep

object Patch:
  given [T](using valueRW: JsonRW[T]): JsonRW[Patch[T]] with
    override def write(value: Patch[T]): JValue = value match
      case Set(value) => valueRW.write(value)
      case Clear      => JNull
      case Keep       => throw TupsonException("Patch.Keep can only be written as an object field")

    override def shouldWriteField(value: Patch[T]): Boolean = value != Keep

    override def parse(path: String, jValue: JValue): Patch[T] = jValue match
      case JNull => Clear
      case other => Set(valueRW.parse(path, other))

    override def default: Option[Patch[T]] = Some(Keep)

case class UserPatch(name: Patch[String], address: Patch[String]) derives JsonRW

package rec {
  case class Node(children: Seq[Node]) derives JsonRW
}

package weird_named {
  case class WeirdNamed(`weird named key`: Int) derives JsonRW
}

type Person = (name: String, age: Int)

case class Gen[T](value: T) derives JsonRW
case class Box[T](gen: Gen[T]) derives JsonRW

sealed trait Expr[T] derives JsonRW
case class Const[T](value: T) extends Expr[T]
case class Add[T](left: Expr[T], right: Expr[T]) extends Expr[T]

type LiteralPerson = (x: "abc")
type LiteralUnion = "abc" | 123 | true
