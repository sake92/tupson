package ba.sake

import ba.sake.tupson.JsonRW
import ba.sake.tupson.JsonRW.toJson

case class Address(street: String)
case class Person(name: String, age: Int, adress: Address) derives JsonRW

enum Color derives JsonRW:
  case Hex(num: String)
  case Yellow()

@main def bla: Unit =
    
    println(Person("Meho", 33, Address("Sebilj")).toJson)

    val c: Color = Color.Hex("FFF")
    println(c.toJson)

    val c2 = Color.Hex("BBB")
    println(c2.toJson)