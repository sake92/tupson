---
title: sttp client4
description: sttp client4 integration
---

# {{ page.title }}

Tupson integrates with the awesome [sttp client4](https://sttp.softwaremill.com/en/latest/) library.  

You will need to add these dependencies:
```scala
ba.sake::tupson-sttp:{{site.data.project.artifact.version}} // in addition to ba.sake::tupson:...

"com.softwaremill.sttp.client4" %% "core" % "4.0.21" // or e.g. "zio", "cats", ...
```

> Real-world example in [sharaf](https://github.com/sake92/sharaf)

## Usage

`tupson-sttp` hooks into `JsonRW`, so any value that can be written/parsed by Tupson can be sent or received via sttp.  
Start by importing both sttp and Tupson:

```scala
import ba.sake.tupson.{given, *}
import ba.sake.sttp.tupson.*
import sttp.client4.*
```

### Request body

Use `asJson` on any `JsonRW` value as the request body:

```scala
case class CreateUser(name: String, age: Int) derives JsonRW

val request = basicRequest
  .post(uri"http://example.com/api/users")
  .body(asJson(CreateUser("Sake", 30)))
```

### Response body

Use the `.response(asJson[B])` handler to parse the response body into a `JsonRW` value:

```scala
case class User(id: Int, name: String, age: Int) derives JsonRW

val user = basicRequest
  .get(uri"http://example.com/api/users/1")
  .response(asJson[User])
  .send(backend)
// Right(User(1, Sake, 30))
```

Tupson's parsing rules apply, including optional fields, defaults and sum types:

```scala
sealed trait Shape derives JsonRW
case class Circle(radius: Double) extends Shape derives JsonRW
case class Square(side: Double) extends Shape derives JsonRW

val shape = basicRequest
  .get(uri"http://example.com/api/shapes/1")
  .response(asJson[Shape])
  .send(backend)
```

### Convenience handlers

Several handlers are provided, mirroring the ones found in other sttp json libraries:

- `asJson[B]` — `Either[ResponseException[String], B]`
- `asJsonOrFail[B]` — like `asJson`, but throws on non-2xx / parse errors
- `asJsonAlways[B]` — tries to parse regardless of the status code
- `asJsonEither[E, B]` — parses the error body into `E` on non-2xx
- `asJsonEitherOrFail[E, B]` — like `asJsonEither`, but throws on parse errors
