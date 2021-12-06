# tupson

Stupid simple zero-config Scala library for writing and reading JSON.  

Notes: It just supports writing currently.



## Writing JSON

These all work out of the box:
```scala
import JsonWriter.toJson

println(true.toJson)    // true
println(1.toJson)       // 1
println(1.123.toJson)   // 1.123
println("abc".toJson)   // "abc"

println(Seq(1, 2, 3).toJson) // [1,2,3]

println(Option(123).toJson)


case class Address(street: String)
case class Person(name: String, age: Int, adress: Address)
println(Person("Meho", 33, Address("Sebilj")).toJson)



```