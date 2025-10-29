---
title: Writing
description: Tupson Writing
---

# {{ page.title }}

Writing is really simple.  
Just call `.toJson` on your data:

```scala
import ba.sake.tupson.{given, *}

case class WriteData(
  bln: Boolean,
  int: Int,
  dbl: Double,
  str: String,
  list: Seq[String]
) derives JsonRW

val data = WriteData(true, 5, 3.14, "xyz", Seq("a", "b"))

data.toJson
// {"str":"xyz","bln":true,"list":["a","b"],"int":5,"dbl":3.14}
```
