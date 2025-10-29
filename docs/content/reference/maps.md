---
title: Maps
description: Working with Maps in Tupson
---

# {{ page.title }}

`Map[String, T]` work as you expect. 
        
```scala
Map(
    "a" -> 5,
    "b" -> 123
).toJson
// {"a":5,"b":123}
```

```scala
""" {"a":5,"b":123} """.parseJson[Map[String, Int]]
// Map(a -> 5, b -> 123)
```

Maps are limited in the sense that the values have to be of same type.  
If you need them different, consider using [case classes](${CaseClasses.ref}) or [dynamic JSON values](${DynamicJson.ref}).

