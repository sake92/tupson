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
```

## Examples

[Examples](${Consts.GhSourcesUrl}/examples/src/main/scala) are runnable with [Mill](https://com-lihaoyi.github.io/mill/mill/Intro_to_Mill.html):

```sh
./mill examples.runMain write
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
