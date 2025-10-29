package ba.sake.tupson

import ba.sake.tupson.namedTuples.given

type Person = (name: String, age: Int)

class ParseSuite extends munit.FunSuite {

  test("parse named tuple") {

    val nt1: Person = (name = "Mujo", age = 35)
    assertEquals(nt1.toJson, """{"age":35,"name":"Mujo"}""")

    val str = """ { "name": "Mujo", "age": 35 } """
    val nt2 = str.parseJson[Person]
    assertEquals(nt2, (name = "Mujo", age = 35))
  }
}
