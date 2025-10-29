---
title: Maintaining backwards compatibility
description: Maintaining backwards compatibility in Tupson
---

# {{ page.title }}


Let's say you have a `case class MyConfig(url: String)`.  
Now you need to add another property: `port: Int`, but **without breaking existing serialized values**.

You have 2 options:
1. use an `Option[Int]`, and set a default value later if it is missing
2. use a `Int = MyDefaultValue` to avoid `Option` gymnastics

## Using Option


Using `Option[T]` is handy when you don't have a default value.  
But it is a bit cumbersome to handle:
```scala
case class MyConfig(url: String, port: Option[Int]) derives JsonRW
parsedConfig.port.getOrElse(...)
```

## Using default value

If you do have a default value for a particular property, by all means do use it.  
It will make your life much easier, you can pretend it was always there:

```scala
case class MyConfig(url: String, port: Int = 1234) derives JsonRW
```