---
title: Using weird key names
description: Using weird key names in Tupson
---

# {{ page.title }}

Sometimes you need spaces or other characters in your JSON keys.  
You can use Scala's "backticks" language feature for that:

```scala
case class Address(`street no`: String) derives JsonRW

val address = Address("My Street 123")

println(address.toJson)
// {"street no":"My Street 123"}
```