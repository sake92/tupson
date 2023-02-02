# tupson

Stupid simple, minimalistic Scala 3 library for writing and reading JSON.

It only does `String <=> T` conversions, no streaming.

## Usage

Setup:
```scala
def ivyDeps = Agg(
  ivy"ba.sake::tupson:0.3.0"
)
def scalacOptions =
  super.scalacOptions() ++ Seq("-Yretain-trees")
```

You can use [scala-cli](https://scala-cli.virtuslab.org/) to try it locally:
```bash
scala-cli scala-cli-example.scala

Compiling project (Scala 3.2.1, JVM)
Compiled project (Scala 3.2.1, JVM)

{"str":"xyz","bln":true,"list":["a","b"],"int":5,"dbl":3.14}
RoundtripData(true,5,3.14,xyz,ArraySeq(a, b))
before == after: true
```

Or you can use [Scastie](https://scastie.scala-lang.org/pQdrpZNiQEOkHAkBvn8YeA) to play with it online.

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
// { "age":33, "name":"Meho", "address":{"street":"Sebilj"} }
```

Note that you don't even need `derives JsonRW` anywhere, although it is recommended for performance reasons!


### Writing enums and sealed traits

```scala
enum Color derives JsonRW:
  case Hex(num: String)
  case Yellow

val color = Color.Hex("FFF")
println(color.toJson)
// {"@type":"Hex","num":"FFF"}
```

The `@type` key is used to figure out what subtype of enum/sealed trait it is.  
Its value is the simple type of class or enum case.  
This makes JSON independent of scala/java package and it is more readable/understandable.

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

"""{ "bln":true }""".parseJson[MyData] // MissingRequiredKeysException: int, s
```


### Missing keys / backwards compatibility

Let's say you had a `case class MyConfig(url: String)`.  
Now you need to add another property: `port: Int`, but without breaking existing serialized values.

You have 2 options:
1. use an `Option[Int]`, and set a default value later if it is missing
2. use a `Int = MyDefaultValue` to avoid `Option` gymnastics

```scala
// option 1
case class MyConfig(url: String, port: Option[Int])
parsedConfig.port.getOrElse(...)

// option 2
case class MyConfig(url: String, port: Int = 1234)
```

See scastie demo here: https://scastie.scala-lang.org/SSSnMIhNS1Ggi3Ttr3iD3A










