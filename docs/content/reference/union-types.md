---
title: Union Types
description: Working with Union Types in Tupson
---

# {{ page.title }}

In Scala 3 you can use union types in the form `A | B | C`.

> Use case: Some APIs support "expanding objects".
E.g. they either return a `String` which is ID of that object, or full object `T` when you send a query string `expand=myObject`.
For this you can leverage union types in natural way: `someKey: String | MyObject`.

When parsing, Tupson will try all types (left to right) until it finds one that parses correctly.  
When writing, Tupson will use its runtime type to determine how to write the value.


```scala
"1".parseJson[Int | String | Boolean]
// 1: Int

""" "bla" """.parseJson[Int | String | Boolean]
// "bla": String
```