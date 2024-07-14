import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`
import $ivy.`ba.sake::mill-hepek::0.0.2`

import mill._, scalalib._, scalajslib._, scalanativelib._, publish._, scalafmt._
import io.kipp.mill.ci.release.CiReleaseModule
import ba.sake.millhepek.MillHepekModule

object tupson extends Module {

  object jvm extends TupsonCommonModule {
    object test extends ScalaTests with CommonTestModule
  }

  object js extends TupsonCommonModule with ScalaJSModule {
    def scalaJSVersion = "1.16.0"
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"io.github.cquiroz::scala-java-time::2.5.0"
    )
    object test extends ScalaJSTests with CommonTestModule
  }

  object native extends TupsonCommonModule with ScalaNativeModule {
    def scalaNativeVersion = "0.5.4"
   // object test extends ScalaNativeTests with CommonTestModule
  }

  trait TupsonCommonModule extends CommonScalaModule with CommonPublishModule with PlatformScalaModule {
    def artifactName = "tupson"

    def ivyDeps = Agg(
      ivy"org.typelevel::jawn-ast::1.6.0"
    )
  }
}

// jvm-only
object `tupson-config` extends CommonScalaModule with CommonPublishModule {
  def moduleDeps = Seq(tupson.jvm)
  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.typesafe:config:1.4.3"
  )
  object test extends ScalaTests with CommonTestModule {
    def forkArgs = Seq("-Dconfig.override_with_env_vars=true")
    def forkEnv = Map("CONFIG_FORCE_envvar_port" -> "1234")
  }
}

object examples extends CommonScalaModule {
  def moduleDeps = Seq(tupson.jvm)
}

object docs extends CommonScalaModule with MillHepekModule {
  def ivyDeps = Agg(
    ivy"ba.sake::hepek:0.22.0"
  )
}

trait CommonScalaModule extends ScalaModule with ScalafmtModule {
  def scalaVersion = "3.4.2"
  def scalacOptions = super.scalacOptions() ++ Seq(
    "-Yretain-trees", // required for default arguments
    "-deprecation",
    "-Xcheck-macros"
  )
}

trait CommonPublishModule extends CiReleaseModule {
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

trait CommonTestModule extends TestModule.Munit {
  def ivyDeps = Agg(
    ivy"org.scalameta::munit::1.0.0"
  )
}
