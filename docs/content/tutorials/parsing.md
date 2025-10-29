---
title: Parsing
description: Tupson Parsing
---

# {{ page.title }}

Let's start with a simple `case class MyData`.  
The class needs to derive the `JsonRW` typeclass.
```scala
import ba.sake.tupson.{given, *}

case class MyData(
    bln: Boolean,
    int: Int,
    s: String
) derives JsonRW
```

Then, you can call `parseJson[T]` function on a `String` to parse it to the desired type:
```scala
val res = """{ "bln":true, "int":5, "s":"dsds" }""".parseJson[MyData]
// MyData(true,5,dsds)
```

## General errors

If parsing fails you will get a `TupsonException`:
```scala
"""{ "bln":true """.parseJson[MyData]
// TupsonException: incomplete JSON
```

## Specific errors

Tupson will give you the most specific error(s) as possible:
```scala
"""{ "bln":123 }""" .parseJson[MyData]
// Key '$.bln' with value '123' should be Boolean but it is Number; Key '$.int' is missing; Key '$.s' is missing
```

## Collecting errors

You can catch `ParsingException` to collect the errors.  
This exception contains `errors: Seq[ParseError]`, list of errors that happened while parsing.  
Every `ParseError` object contains a [JSONPath](https://www.ietf.org/archive/id/draft-goessner-dispatch-jsonpath-00.html#name-jsonpath-examples) path to the field which has errors in it.  
You saw some of it in the error stacktrace above.

```scala
try {
  """{ "bln":123 } """ .parseJson[MyData]
} catch {
  case pe: ParsingException =>
    val errors = pe.errors.mkString("\\n")
    println(s"errors:\\n${errors}")
}
// errors:
// ParseError($.bln,should be Boolean but it is Number,Some(123))
// ParseError($.int,is missing,None)
// ParseError($.s,is missing,None)
```
