# tupson

Stupid simple, minimalistic Scala 3 library for writing and reading JSON.

It only does `String <=> T` conversions, no streaming.

## Usage

Setup in sbt:
```scala
libraryDependencies ++= Seq(
  "ba.sake" %%% "tupson" % "0.5.0"
)
scalacOptions ++= Seq(
  "-Yretain-trees"
)
```

Setup in Mill:
```scala
def ivyDeps = Agg(
  ivy"ba.sake::tupson:0.5.0"
)
def scalacOptions = super.scalacOptions() ++ Seq(
  "-Yretain-trees"
)
```

Setup in scala-cli:
```scala
//> using lib "ba.sake::tupson:0.5.0"
```

You can also use [Scastie](https://scastie.scala-lang.org/EPtuU6OURsKHykCm7uaj7w) to play with `tupson` online.


---
## Examples

[Examples](examples/src/main/scala) are runnable with [Mill](https://com-lihaoyi.github.io/mill/mill/Intro_to_Mill.html):

```sh
./mill examples.runMain write
```

---
---
## Core types

```scala

// write a value to JSON string
val myValue = 123
println(123.toJson) // "123"

// parse a value from JSON string
val myParsedValue = """ 123 """.parseJson[Int]
println(myParsedValue) // 123
```

Simple types: `Int`, `Double`, `Boolean`, `String` etc work out of the box.

### Collections

`List[T]`, `Seq[T]`, `Array[T]` are supported.  
> Note that `T` needs to have a `JsonRW[T]` given instance!

### Maps

`Map[String, T]` work as you expect.  
> Note that `T` needs to have a `JsonRW[T]` given instance!

### Options

`Option[T]` work as you expect.  
`None` corresponds to JSON's `null`.  
> Note that `T` needs to have a `JsonRW[T]` given instance!


---
## Case classes

```scala
case class Address(street: String)
case class Person(name: String, age: Int, address: Address) derives JsonRW

val person = Person("Meho", 33, Address("Sebilj"))
println(person.toJson)
// { "age":33, "name":"Meho", "address":{"street":"Sebilj"} }
```

Note that you *don't even need* `derives JsonRW` anywhere, although it is recommended for compile-performance reasons!  
`Tupson` will generate a `JsonRW[T]` typeclass instance if it can not find one.

---
## Simple enums 

Simple enums (Java-esque ones) are serialized as strings.
```scala
enum Semaphore derives JsonRW:
  case Red, Yellow, Green

val semaphore = Semaphore.Red
println(semaphore.toJson)
// "Red"
```

---
## Enum ADTs and sealed traits

```scala
enum Color derives JsonRW:
  case Hex(num: String)
  case Yellow

val color = Color.Hex("FFF")
println(color.toJson)
// {"@type":"Hex","num":"FFF"}
```

The `@type` key is used to specify the *subtype* of enum/sealed trait.  
Its value is the simple type of class or enum case.  
This makes JSON independent of scala/java package and it is more readable.

---
## Unusual/weird key names

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

---
---
## Parsing

If parsing fails you get a `TupsonException`.

```scala
case class MyData(
    bln: Boolean,
    int: Int,
    s: String
) derives JsonRW

"""{ "bln":true, "int":5, "s":"dsds" }""".parseJson[MyData]
// success

"""{ "bln":true """.parseJson[MyData]
// incomplete JSON

"""{ "bln":true }""".parseJson[MyData]
// Key 'bln': Expected Boolean but got 123: Number
// Key 'int': Missing value
// Key 's': Missing value
```

---
## Missing keys / backwards compatibility

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










