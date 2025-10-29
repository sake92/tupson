---
title: Typesafe config
description: Typesafe config integration
---

# {{ page.title }}


Tupson integrates with the awesome [Typesafe config library](https://github.com/lightbend/config).  

You will need to add this dependency:
```scala
{{site.data.project.config_artifact.org}}::{{site.data.project.config_artifact.name}}:{{site.data.project.config_artifact.version}} // scala-cli

mvn"{{site.data.project.config_artifact.org}}::{{site.data.project.config_artifact.name}}:{{site.data.project.config_artifact.version}}" // mill

"{{site.data.project.config_artifact.org}}" %% "{{site.data.project.config_artifact.name}}" % "{{site.data.project.config_artifact.version}}" // sbt

```

> Real-world example in [sharaf-petclinic](https://github.com/sake92/sharaf-petclinic/blob/main/app/src/ba/sake/sharaf/petclinic/PetclinicConfig.scala)

## Usage

You can call `.parseConfig[MyConf]` function on a `Config` to parse it to the desired type:

```scala
import java.net.URL
import com.typesafe.config.ConfigFactory
import ba.sake.tupson.{given, *}
import ba.sake.tupson.config.*

case class MyConf(
  port: Int,
  url: URL,
  string: String,
  seq: Seq[String]
) derives JsonRW

val rawConfig = ConfigFactory.parseString("""
  port = 7777
  url = "http://example.com"
  string = "str"
  seq = [a, "b", c]
""")

val myConf = rawConfig.parseConfig[MyConf]
// MyConf(7777,http://example.com,str,List(a, b, c))
```
