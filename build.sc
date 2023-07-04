import mill._, scalalib._, scalajslib._, publish._, scalafmt._

import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`
import io.kipp.mill.ci.release.CiReleaseModule

object tupson extends Module {

  object jvm extends TupsonPublishModule

  object js extends TupsonPublishModule with ScalaJSModule {
    def scalaJSVersion = "1.13.1"
  }
}

object examples extends TupsonCommonModule {
  def moduleDeps = Seq(tupson.jvm)
}

trait TupsonCommonModule extends SbtModule with ScalafmtModule {
  def scalaVersion = "3.3.0"

  def scalacOptions = super.scalacOptions() ++ Seq(
    "-deprecation",
    "-Yretain-trees" // required for default arguments
  )

  def ivyDeps = Agg(
    ivy"org.typelevel::jawn-ast::1.5.0"
  )

  // shared sources between jvm and js
  override def sources = T.sources(
    super.sources() ++ Seq(
      PathRef(os.pwd / "tupson" / "shared" / "src" / "main" / "scala")
    )
  )

  object test extends Tests with TestModule.Munit with ScalafmtModule {
    def ivyDeps = Agg(
      ivy"org.scalameta::munit::0.7.29"
    )
    override def sources = T.sources(
      super.sources() ++ Seq(
        PathRef(os.pwd / "tupson" / "shared" / "src" / "test" / "scala")
      )
    )
  }
}

trait TupsonPublishModule extends TupsonCommonModule with CiReleaseModule {

  def artifactName = "tupson"

  def pomSettings = PomSettings(
    organization = "ba.sake",
    url = "https://github.com/sake92/tupson",
    licenses = Seq(License.Common.Apache2),
    versionControl = VersionControl.github("sake92", "tupson"),
    description = "Tupson JSON library",
    developers = Seq(
      Developer("sake92", "Sakib Hadžiavdić", "https://sake.ba")
    )
  )
}
