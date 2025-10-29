---
title: Option
description: Working with Option in Tupson
---

# {{ page.title }}

`Option[T]` work (probably?) as you expect.  
`None` corresponds to JSON's `null`.  
    
```scala
Option.empty[String].toJson
// null

Option("str").toJson
// "str"
```

```scala
""" null """.parseJson[Option[String]]
// None

""" "str" """.parseJson[Option[String]]
// Some(str)
```


