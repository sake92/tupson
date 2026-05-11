---
title: Literal Types
description: Working with Literal Types in Tupson
---

# {{ page.title }}

```scala
type Marker = (kind: "abc")

val marker: Marker = (kind = "abc")
assert(marker.toJson(spaces = 0) == """{"kind":"abc"}""")
assert("""{"kind":"abc"}""".parseJson[Marker] == marker)
```
