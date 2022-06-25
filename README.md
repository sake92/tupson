# tupson

Stupid simple Scala 3 library for writing and reading JSON.  

Notes: It just supports writing currently.



## Write

Run [ammonite](https://ammonite.io/) (for [Scala 3](https://github.com/com-lihaoyi/Ammonite/releases/download/2.5.4/3.1-2.5.4)) to try it:

```bash
$ amm

@ import $ivy.`ba.sake::tupson:0.1.5`
@ import ba.sake.tupson.*
```

### Writing simple types
```scala
import ba.sake.tupson.*

println(true.toJson)    // true
println(1.123.toJson)   // 1.123
println("abc".toJson)   // "abc"

println(Seq(1, 2, 3).toJson) // [1,2,3]

println(Option(123).toJson)         // 123
println(Option.empty[Int].toJson)   // null
```

### Writing case classes

```scala
import ba.sake.tupson.*

case class Address(street: String)
case class Person(name: String, age: Int, adress: Address) derives JsonRW

val person = Person("Meho", 33, Address("Sebilj"))
println(person.toJson)
// {"adress":{"street":"Sebilj"},"age":33,"name":"Meho"}
```

Note that you don't need `derives JsonRW` on Address, although it is recommended!


### Writing enums and sealed traits

```scala
enum Color derives JsonRW:
  case Hex(num: String)
  case Yellow

val color = Color.Hex("FFF")
println(color.toJson)
// {"@type":"ba.sake.Color.Hex","num":"FFF"}
```

