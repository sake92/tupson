package files.tutorials

import utils.*
import Bundle.*, Tags.*

object Index extends TutorialPage {

  override def pageSettings = super.pageSettings
    .withTitle("Tutorials")
    .withLabel("Tutorials")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    "Quickstart",
    s"""
      Hello world!
    """.md,
    List(
      Section(
        "Scastie",
        s"""
        Quickest way to start playing with ${Consts.ProjectName} is with this [Scastie example](https://scastie.scala-lang.org/KQfj7lUST0i2Iz4lZOEwOQ).
        """.md
      ),
      Section(
        "Mill",
        div(
          chl.scala(s"""
          def ivyDeps = super.ivyDeps() ++ Agg(
            ivy"${Consts.ArtifactOrg}::${Consts.ArtifactName}:${Consts.ArtifactVersion}"
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
            "${Consts.ArtifactOrg}" %% "${Consts.ArtifactName}" % "${Consts.ArtifactVersion}"
          )
          scalacOptions ++= Seq("-Yretain-trees")
          """)
        )
      ),
      Section(
        "Scala CLI",
        div(
          chl.scala(s"""
          //> using dep ${Consts.ArtifactOrg}::${Consts.ArtifactName}:${Consts.ArtifactVersion}
          """)
        )
      ),
      Section(
        "Examples",
        div(
          s"""
          [Examples](${Consts.GhSourcesUrl}/examples/src/main/scala) are runnable with [Mill](https://com-lihaoyi.github.io/mill/mill/Intro_to_Mill.html):
          """.md,
          chl.bash(s"""
          ./mill examples.runMain write
          """)
        )
      )
    )
  )
}
