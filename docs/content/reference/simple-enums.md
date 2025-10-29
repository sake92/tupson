---
title: Simple Enums
description: Working with Simple Enums in Tupson
---

# {{ page.title }}


Simple enums are (de)serialized as JSON strings.  
By "simple" we mean an enum that only has "singleton cases" as defined in the [docs](https://docs.scala-lang.org/scala3/reference/enums/desugarEnums.html).  
That is, enum who's `case`s don't have a parameter clause.

```scala
enum Semaphore derives JsonRW:
  case Red, Yellow, Green

val semaphore = Semaphore.Red
println(semaphore.toJson)
// "Red"
```
