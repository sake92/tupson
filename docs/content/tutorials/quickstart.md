---
title: Quickstart
description: Tupson Quickstart
---

# {{ page.title }}

## Scastie

Quickest way to start playing with {{site.data.project.name}} is with this [Scastie example](https://scastie.scala-lang.org/KQfj7lUST0i2Iz4lZOEwOQ).

## Mill

```scala
def mvnDeps = super.mvnDeps() ++ Seq(
  mvn"{{site.data.project.artifact.org}}::{{site.data.project.artifact.name}}:{{site.data.project.artifact.version}}"
)
def scalacOptions = super.scalacOptions() ++ Seq("-Yretain-trees")
```

## Sbt

```scala
libraryDependencies ++= Seq(
  "{{site.data.project.artifact.org}}" %% "{{site.data.project.artifact.name}}" % "{{site.data.project.artifact.version}}"
)
scalacOptions ++= Seq("-Yretain-trees")
```

## Scala CLI

```scala
//> using dep {{site.data.project.artifact.org}}::{{site.data.project.artifact.name}}:{{site.data.project.artifact.version}}
```

## Examples

[Examples](${Consts.GhSourcesUrl}/examples/src/main/scala) are runnable with [Mill](https://com-lihaoyi.github.io/mill/mill/Intro_to_Mill.html):

```sh
./mill examples.runMain write
```
