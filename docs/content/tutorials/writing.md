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
//   "bln": true,
//   "dbl": 3.14,
//   "int": 5,
//   "list": [
//     "a",
//     "b"
//   ],
//   "str": "xyz"
// }
```

By default the output is sorted and pretty-printed. You can still opt into dense or unsorted output:

```scala
data.toJson(spaces = 0)
// {"bln":true,"dbl":3.14,"int":5,"list":["a","b"],"str":"xyz"}

data.toJson(sort = false)
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

data.toJson(spaces = 4)
// same data, with 4-space indentation and sorted keys
```
