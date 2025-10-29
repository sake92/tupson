---
title: Collections
description: Working with Collections in Tupson
---

# {{ page.title }}

`Seq[T]`, `List[T]`, `Set[T]`, `Array[T]` are supported.  
        
```scala
Seq.empty[String].toJson
// []

Seq("a", "b").toJson
// ["a","b"]
```

```scala
""" [] """.parseJson[Seq[String]]
// Seq()

""" ["a","b"] """.parseJson[Seq[String]]
// Seq(a,b)
```