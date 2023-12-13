package files.tutorials

import utils.*
import Bundle.*, Tags.*

object ParsingConfig extends TutorialPage {

  override def pageSettings = super.pageSettings
    .withTitle("Typesafe config integration")
    .withLabel("Typesafe config")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    "Typesafe config integration",
    div(
      s"""
      ${Consts.ProjectName} integrates with the awesome [Typesafe config library](https://github.com/lightbend/config).  
      You can rely on all ${Consts.ProjectName}'s features like ADTs, enums, etc.

      Import it in your build:
      """.md,
      chl.scala(s"""
        // mill
        def ivyDeps = super.ivyDeps() ++ Agg(
          ivy"tupson-config::${Consts.ArtifactName}:${Consts.ArtifactVersion}"
        )

        // sbt
        libraryDependencies ++= Seq(
          "tupson-config" %% "${Consts.ArtifactName}" % "${Consts.ArtifactVersion}"
        )

        // scala-cli
        //> using dep tupson-config::${Consts.ArtifactName}:${Consts.ArtifactVersion}
      """),
      s"Then, you can call `.parse[MyConf]` function on a `Config` to parse it to the desired type:".md,
      chl.scala(s"""
      import com.typesafe.config.ConfigFactory
      import ba.sake.tupson.{given, *}
      import ba.sake.tupson.config.*

      case class MyConf(
        port: Int,
        url: URL,
        string: String,
        seq: Seq[String]
      ) derives JsonRW

      val rawConfig = ConfigFactory.load()
      val myConf = rawConfig.parse[MyConf]()
      // MyConf(7777, URL("http://example.com"), "str", Seq("a", "b", "c"))
      """)
    )
  )
}
