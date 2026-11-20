---
title: Quickstart
description: Tupson Quickstart
---

# {{ page.title }}

## Scastie

Quickest way to start playing with Tupson is with this [Scastie example](https://scastie.scala-lang.org/KQfj7lUST0i2Iz4lZOEwOQ).

## Mill

```scala
def mvnDeps = super.mvnDeps() ++ Seq(
  mvn"ba.sake::tupson:{{site.data.project.artifact.version}}"
)
def scalacOptions = super.scalacOptions() ++ Seq("-Yretain-trees")
```

## Sbt

```scala
libraryDependencies ++= Seq(
  "ba.sake" %% "tupson" % "{{site.data.project.artifact.version}}"
)
scalacOptions ++= Seq("-Yretain-trees")
```

## Scala CLI

```scala
//> using dep ba.sake::tupson:{{site.data.project.artifact.version}}
//> using option -Yretain-trees
```

## Examples

From a checkout of the [Tupson repository](https://github.com/sake92/tupson), run the [examples](https://github.com/sake92/tupson/tree/main/examples/src/main/scala) with Deder.

```sh
deder exec -m examples -t runMain write
```

## Generic derives

Parameterized products and sums can derive `JsonRW` too:

```scala
import ba.sake.tupson.{*, given}

case class Gen[T](value: T) derives JsonRW
case class Box[T](gen: Gen[T]) derives JsonRW

sealed trait Expr[T] derives JsonRW
case class Const[T](value: T) extends Expr[T]
case class Add[T](left: Expr[T], right: Expr[T]) extends Expr[T]
```

Note: this covers parameterized ADTs. Full true-GADT case refinements still require manual codecs.
