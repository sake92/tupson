import mill._, scalalib._, scalajslib._, publish._, scalafmt._

import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.1.4`
import de.tobiasroeser.mill.vcs.version.VcsVersion

object tupson extends Module {

  object jvm extends TupsonPublishModule

  object js extends TupsonPublishModule with ScalaJSModule {
    def scalaJSVersion = "1.13.0"
  }
}

object examples extends TupsonCommonModule {
  def moduleDeps = Seq(tupson.jvm)
}

trait TupsonCommonModule extends SbtModule with ScalafmtModule {
  def scalaVersion = "3.2.2"

  def scalacOptions = super.scalacOptions() ++ Seq(
    "-Yretain-trees", // Required by magnolia
  )

  def ivyDeps = Agg(
    ivy"org.typelevel::jawn-ast::1.4.0",
    ivy"com.lihaoyi::sourcecode::0.3.0",
    ivy"com.github.sake92.magnolia::magnolia::disambiguate-singleton-enums-SNAPSHOT"
  )

  def repositoriesTask() = T.task { super.repositoriesTask() ++ Seq(
    coursier.maven.MavenRepository("https://jitpack.io")
  )}

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

trait TupsonPublishModule extends TupsonCommonModule with PublishModule {

  def artifactName = "tupson"

  override def publishVersion: T[String] = VcsVersion.vcsState().format()

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