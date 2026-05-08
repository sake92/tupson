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
// {
//   "str": "xyz",
//   "bln": true,
//   "list": [
//     "a",
//     "b"
//   ],
//   "int": 5,
//   "dbl": 3.14
// }
```

Spacing and sorting are configurable:

```scala
data.toJson(spaces = 0)
// {"str":"xyz","bln":true,"list":["a","b"],"int":5,"dbl":3.14}

data.toJson(sort = true)
// {
//   "bln": true,
//   "dbl": 3.14,
//   "int": 5,
//   "list": [
//     "a",
//     "b"
//   ],
//   "str": "xyz"
// }

data.toJson(spaces = 4, sort = true)
// same data, with 4-space indentation and sorted keys
```
