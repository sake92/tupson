

# tupson

Stupid simple, minimalistic, Scala 3 library for writing and reading JSON.

Docs at https://sake92.github.io/tupson

Supports Scala, ScalaJS and ScalaNative.

## Cheatsheet

All examples use:

```scala
import ba.sake.tupson.{*, given}
```

**Case classes** derive `JsonRW` and map naturally to JSON objects, including nested products. See [case classes](https://sake92.github.io/tupson/reference/case-classes.html).

```scala
case class Address(street: String) derives JsonRW
case class Person(name: String, age: Int, address: Address) derives JsonRW

Person("Meho", 33, Address("Sebilj")).toJson(spaces = 0, sort = false)
// {"name":"Meho","age":33,"address":{"street":"Sebilj"}}
```

**Built-in types** work out of the box for primitives, `Option`, collections, and `Map[String, T]`. See [Option](https://sake92.github.io/tupson/reference/option.html), [collections](https://sake92.github.io/tupson/reference/collections.html), and [maps](https://sake92.github.io/tupson/reference/maps.html).

```scala
case class Payload(tags: Seq[String], port: Option[Int], flags: Map[String, Boolean]) derives JsonRW

Payload(Seq("prod", "blue"), Some(8080), Map("safe" -> true)).toJson(spaces = 0, sort = false)
// {"tags":["prod","blue"],"port":8080,"flags":{"safe":true}}
```

**Missing keys** fall back to constructor defaults and built-in `JsonRW.default` values for types like `Option`, `Seq`, and `Map`.

```scala
case class Conf(host: Option[String] = Some("localhost"), ports: Seq[Int] = Seq.empty, labels: Map[String, String] = Map.empty) derives JsonRW

"""{}""".parseJson[Conf]
// Conf(Some("localhost"),List(),Map())
```

**Sum types** use `@type` by default, and you can rename the discriminator with `@discriminator`. See [sum types](https://sake92.github.io/tupson/reference/sum-types.html).

```scala
sealed trait Expr derives JsonRW
case class Const(value: Int) extends Expr
case class Add(left: Expr, right: Expr) extends Expr

Add(Const(1), Const(2)).toJson(spaces = 0)
// {"@type":"Add","left":{"@type":"Const","value":1},"right":{"@type":"Const","value":2}}
```

**Generic ADTs** work too for parameterized products and sums, so reusable wrappers and expression trees can still derive automatically. See [quickstart](https://sake92.github.io/tupson/tutorials/quickstart.html).

```scala
case class Gen[T](value: T) derives JsonRW

sealed trait ExprT[T] derives JsonRW
case class ConstT[T](value: T) extends ExprT[T]
case class AddT[T](left: ExprT[T], right: ExprT[T]) extends ExprT[T]

val expr: ExprT[Gen[Int]] = AddT(ConstT(Gen(1)), ConstT(Gen(2)))
expr.toJson(spaces = 0)
// {"@type":"AddT","left":{"@type":"ConstT","value":{"value":1}},"right":{"@type":"ConstT","value":{"value":2}}}
```

**Simple enums** with only singleton cases are encoded as JSON strings. See [simple enums](https://sake92.github.io/tupson/reference/simple-enums.html).

```scala
enum Semaphore derives JsonRW:
  case Red, Yellow, Green

Semaphore.Red.toJson
// "Red"
```

**Union types** parse left-to-right and write using the runtime type of the value. See [union types](https://sake92.github.io/tupson/reference/union-types.html).

```scala
"""{"name":"Mujo","age":35}""".parseJson[(name: String, age: Int) | String]
// (name = "Mujo", age = 35)

val value: Int | String = "abc"
value.toJson
// "abc"
```

**Named tuples** have experimental support, including literal members. See [named tuples](https://sake92.github.io/tupson/reference/named-tuples.html).

```scala
type PersonRow = (name: String, age: Int)

val row: PersonRow = (name = "Mujo", age = 35)
row.toJson(spaces = 0, sort = false)
// {"name":"Mujo","age":35}
```

**Literal types** are supported in case classes, named tuples, and unions, and parsing enforces the exact literal value.

```scala
case class Marker(kind: "abc") derives JsonRW

Marker("abc").toJson(spaces = 0, sort = true)
// {"kind":"abc"}

"""{"kind":"abc"}""".parseJson[Marker]
// Marker("abc")
```

**Dynamic JSON** is available through Jawn's `JValue` when one field needs to stay untyped. See [dynamic JSON](https://sake92.github.io/tupson/reference/dynamic-json.html).

```scala
import org.typelevel.jawn.ast.JValue

case class Event(kind: String, data: JValue) derives JsonRW
```

**Platform support** covers JVM, Scala.js, and Scala Native, and the JVM-only `tupson-config` module adds `parseConfig[T]` for Typesafe Config. See [quickstart](https://sake92.github.io/tupson/tutorials/quickstart.html) and [Typesafe Config](https://sake92.github.io/tupson/tutorials/parsing-config.html).
