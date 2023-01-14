# tupson

Stupid simple/minimalistic Scala 3 library for writing and reading JSON.

It only does `String <=> T` conversions, no streaming.

## Write

You can use [ammonite](https://ammonite.io/) (for [Scala 3](https://github.com/com-lihaoyi/Ammonite/releases/download/2.5.4/3.1-2.5.4)) to try it.  
Or [scala-cli](https://scala-cli.virtuslab.org/).

```bash
$ amm

@ import $ivy.`ba.sake::tupson:0.2.0`
@ import ba.sake.tupson.*
```

### Writing simple types
```scala
println(true.toJson)    // true
println(1.123.toJson)   // 1.123
println("abc".toJson)   // "abc"

println(Seq(1, 2, 3).toJson) // [1,2,3]

println(Option(123).toJson)         // 123
println(Option.empty[Int].toJson)   // null

val map = Map(
  "x" -> "xyz",
  "a" -> "abc"
)
println(map.toJson)   // { "x":"xyz", "a":"abc" }
```

### Writing case classes

```scala
case class Address(street: String)
case class Person(name: String, age: Int, address: Address) derives JsonRW

val person = Person("Meho", 33, Address("Sebilj"))
println(person.toJson)
// {"address":{"street":"Sebilj"},"age":33,"name":"Meho"}
```

Note that you don't even need `derives JsonRW` anywhere, although it is recommended for performance reasons!


### Writing enums and sealed traits

```scala
enum Color derives JsonRW:
  case Hex(num: String)
  case Yellow

val color = Color.Hex("FFF")
println(color.toJson)
// {"@type":"ba.sake.Color.Hex","num":"FFF"}
```

### Unusual/weird key names

You can use the Scala's "backticks" language feature to use weird names for keys:

```scala
case class Address(`street no`: String)

val address = Address("My Street 123")
println(address.toJson)
// {"street no":"My Street 123"}
```

Benefits:
- your code is easy for "grep"/Ctrl+F
- no mismatch between serialized version and your code
- your internal/core models are separate from JSON, as they should be
- mapping between models is explicit

## Parse

Same as above applies to parsing.  
You will get a `TupsonException` if parsing fails.

```scala
case class MyData(
    bln: Boolean,
    int: Int,
    s: String
) derives JsonRW

"""{ "bln":true, "int":5, "s":"dsds" }""".parseJson[MyData] // success

"""{ "bln":true """.parseJson[MyData]  // TupsonException: incomplete JSON

"""{ "bln":true }""".parseJson[MyData] // MissingKeysException: int, s
```
