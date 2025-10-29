package ba.sake.tupson

import namedTuples.given

@main def bla(): Unit =
  type NT = (name: String, age: Int)
  val nt1: NT = (name = "Tupson", age = 35)
  println(nt1.toJson)

  val str = """ { "name": "Tupson", "age": 35 } """
  val nt2 = str.parseJson[NT]
  println(nt2)
