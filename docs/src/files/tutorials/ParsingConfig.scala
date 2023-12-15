package files.tutorials

import utils.*
import Bundle.*, Tags.*

object ParsingConfig extends TutorialPage {

  override def pageSettings = super.pageSettings
    .withTitle("Typesafe config integration")
    .withLabel("Typesafe config")

  override def blogSettings =
    super.blogSettings.withSections(firstSection, usageSection)

  val firstSection = Section(
    "Quickstart",
    s"""
      ${Consts.ProjectName} integrates with the awesome [Typesafe config library](https://github.com/lightbend/config).  
      You can rely on all ${Consts.ProjectName}'s features like ADTs, enums, etc.

      Import it in your build:
      """.md,
    List(
      Section(
        "Mill",
        div(
          chl.scala(s"""
          def ivyDeps = super.ivyDeps() ++ Agg(
            ivy"${Consts.ArtifactOrg}::${Consts.ConfigArtifactName}:${Consts.ArtifactVersion}"
          )
          def scalacOptions = super.scalacOptions() ++ Seq("-Yretain-trees")
          """)
        )
      ),
      Section(
        "Sbt",
        div(
          chl.scala(s"""
          libraryDependencies ++= Seq(
            "${Consts.ArtifactOrg}" %% "${Consts.ConfigArtifactName}" % "${Consts.ArtifactVersion}"
          )
          scalacOptions ++= Seq("-Yretain-trees")
          """)
        )
      ),
      Section(
        "Scala CLI",
        div(
          chl.scala(s"""
          //> using dep ${Consts.ArtifactOrg}::${Consts.ConfigArtifactName}:${Consts.ArtifactVersion}
          """)
        )
      ),
      Section(
        "Scastie",
        s"""
        You can also use this [Scastie example](https://scastie.scala-lang.org/qTktyoG3QgaUYCQ9ODKHlw) to try ${Consts.ProjectName} online.
        """.md
      ),
      Section(
        "Examples",
        div(
          s"""
          Real-world example in [sharaf-petclinic](https://github.com/sake92/sharaf-petclinic/blob/main/app/src/ba/sake/sharaf/petclinic/PetclinicConfig.scala)
          """.md
        )
      )
    )
  )

  val usageSection = Section(
    "Usage",
    div(
      s"Then, you can call `.parseConfig[MyConf]` function on a `Config` to parse it to the desired type:".md,
      chl.scala(s"""
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

      val rawConfig = ConfigFactory.parseString(${tq}
        port = 7777
        url = "http://example.com"
        string = "str"
        seq = [a, "b", c]
      ${tq})

      val myConf = rawConfig.parseConfig[MyConf]
      // MyConf(7777,http://example.com,str,List(a, b, c))
      """)
    )
  )
}
