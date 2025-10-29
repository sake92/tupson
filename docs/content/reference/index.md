---
title: Reference
description: Tupson Reference
pagination:
  enabled: false
---

# {{ page.title }}

{%

set references = [
    { label: "Option", url: "/reference/option.html" },
    { label: "Collections", url: "/reference/collections.html" },
    { label: "Maps", url: "/reference/maps.html" },
    { label: "Simple Enums", url: "/reference/simple-enums.html" },
    { label: "Case Classes", url: "/reference/case-classes.html" },
    { label: "Sum Types", url: "/reference/sum-types.html" },
    { label: "Union Types", url: "/reference/union-types.html" },
    { label: "Dynamic Json", url: "/reference/dynamic-json.html" }
]

%}

{% for reference in references %}- [{{ reference.label }}]({{ reference.url}})
{% endfor %}


Simple types: `Int`, `Double`, `Boolean`, `String` etc work out of the box.

```scala
import ba.sake.tupson.{given, *}

// write a value to JSON string
val myValue = 123
println(123.toJson) // 123

// parse a value from JSON string
val myParsedValue = ${tq}123${tq}.parseJson[Int]
println(myParsedValue) // 123
```

