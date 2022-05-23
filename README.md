# tupson

Stupid simple Scala library for writing and reading JSON.  

Notes: It just supports writing currently.



## Write

Run [ammonite](https://ammonite.io/) (for [Scala 3](https://github.com/com-lihaoyi/Ammonite/releases/download/2.4.1/3.0-2.4.1)) to try it:

```bash
$ amm

@ import $ivy.`ba.sake::tupson:0.1.4`
@ import ba.sake.tupson.JsonRW
@ import ba.sake.tupson.JsonRW.toJson
```

### Writing simple types
```scala
import ba.sake.tupson.JsonRW
import ba.sake.tupson.JsonRW.toJson

println(true.toJson)    // true
println(1.123.toJson)   // 1.123
println("abc".toJson)   // "abc"

println(Seq(1, 2, 3).toJson) // [1,2,3]

println(Option(123).toJson)         // 123
println(Option.empty[Int].toJson)   // null
```

### Writing case classes

```scala
case class Address(street: String)
case class Person(name: String, age: Int, adress: Address) derives JsonRW

println(
    Person("Meho", 33, Address("Sebilj")).toJson
)
// {"adress":{"street":"Sebilj"},"age":33,"name":"Meho"}
```

Note that you don't need `derives JsonRW` on Address, although it is recommended!


### Writing enums and sealed traits

```scala
enum Color derives JsonRW:
  case Hex(num: String)
  case Yellow

println(Color.Hex("FFF").toJson)
// {"@type":"ba.sake.Color$Hex","num":"FFF"}
```

