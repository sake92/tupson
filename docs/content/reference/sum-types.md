---
title: Sum Types
description: Working with Sum Types in Tupson
---

# {{ page.title }}

Here we mean `sealed trait`s, `sealed class`es and
  [non-singleton](https://docs.scala-lang.org/scala3/reference/enums/desugarEnums.html) `enum`s.  
Since they have one or more subtypes, we need to disambiguate between them somehow.  

The default discriminator Tupson uses is the `@type` key.  
Its value is the *simple type name* of class or enum case.  
This makes JSON independent of scala/java package and it is more readable.

Example:
```scala
enum Color derives JsonRW:
  case Hex(num: String)
  case Yellow

val color = Color.Hex("FFF")

println(color.toJson)
// { "@type":"Hex", "num":"FFF" }
```

## Custom discriminator key

You can use some other key by annotating the sum type with `@discriminator`:".md,

```scala
@discriminator("myOtherKey")
enum Color derives JsonRW ...

println(color.toJson)
// { "myOtherKey":"Hex", "num":"FFF" }
```