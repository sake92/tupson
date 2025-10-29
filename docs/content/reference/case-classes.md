---
title: Case Classes
description: Working with Case Classes in Tupson
---

# {{ page.title }}

Case classes are (de)serialized as JSON objects:

```scala
import ba.sake.tupson.{given, *}

case class Address(street: String) derives JsonRW
case class Person(name: String, age: Int, address: Address) derives JsonRW

val person = Person("Meho", 33, Address("Sebilj"))

println(person.toJson)
// { "age":33, "name":"Meho", "address": { "street":"Sebilj" } }
```
