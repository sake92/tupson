---
title: Dynamic JSON
description: Working with Dynamic JSON in Tupson
---

# {{ page.title }}

Sometimes you don't know exactly the structure of incoming JSON payload.  
Since Tupson uses [Jawn's](https://github.com/typelevel/jawn) under cover,
you can use its `JValue` directly:

```scala
import org.typelevel.jawn.ast.JValue

case class MyData(
  str: String,
  dynamic: JValue // this key's data can be anything: null, string, object, sequence...
) derives JsonRW
```
