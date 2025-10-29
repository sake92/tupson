---
title: Named Tuples
description: Working with Named Tuples in Tupson
---

# {{ page.title }}

In Scala 3 you can use named tuples in the form `(a = "mystring", b = 123)`.  
Tupson has experimental support for it.

You need to add an extra dependency and use scala 3.7+:
```scala
ba.sake::tupson-next:{{site.data.project.artifact.version}} // scala-cli

mvn"ba.sake::tupson-next:{{site.data.project.artifact.version}}" // mill

"ba.sake" %% "tupson-next" % "{{site.data.project.artifact.version}}" // sbt

```

Example:


```scala
type Person = (name: String, age: Int)

val nt1: Person = (name = "Mujo", age = 35)
println(nt1.toJson)
// {"age":35,"name":"Mujo"}

val str = """ { "name": "Mujo", "age": 35 } """
val nt2 = str.parseJson[Person]
// (name = "Mujo", age = 35)
```
