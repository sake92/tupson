import mill._, scalalib._, scalajslib._, publish._, scalafmt._

import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`
import io.kipp.mill.ci.release.CiReleaseModule

object tupson extends Module {

  object jvm extends TupsonCommonModule {
    object test extends ScalaTests with TupsonCommonTestModule
  }

  object js extends TupsonCommonModule with ScalaJSModule {
    def scalaJSVersion = "1.14.0"
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"io.github.cquiroz::scala-java-time::2.5.0"
    )
    object test extends ScalaJSTests with TupsonCommonTestModule
  }

  trait TupsonCommonModule extends CommonScalaModule with PlatformScalaModule with CiReleaseModule with ScalafmtModule {
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
    def ivyDeps = Agg(
      ivy"org.typelevel::jawn-ast::1.5.0"
    )
  }

  trait TupsonCommonTestModule extends TestModule.Munit {
    def ivyDeps = Agg(
      ivy"org.scalameta::munit::0.7.29"
    )
  }

}

object examples extends CommonScalaModule {
  def moduleDeps = Seq(tupson.jvm)
}

trait CommonScalaModule extends ScalaModule {
  def scalaVersion = "3.3.1"
  def scalacOptions = super.scalacOptions() ++ Seq(
    "-Yretain-trees", // required for default arguments
    "-deprecation",
    "-Xcheck-macros"
  )
}
