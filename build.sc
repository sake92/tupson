import mill._, scalalib._, scalajslib._, publish._

import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.1.4`
import de.tobiasroeser.mill.vcs.version.VcsVersion

object tupson extends Module {

  object jvm extends TupsonCommonModule

  object js extends TupsonCommonModule with ScalaJSModule {
    def scalaJSVersion = "1.10.0"
  }
}

trait TupsonCommonModule extends SbtModule with PublishModule {

  def artifactName = "tupson"
  
  def scalaVersion = "3.1.2"

  def ivyDeps = Agg(
    ivy"org.typelevel::jawn-ast::1.3.2",
    ivy"org.typelevel::shapeless3-deriving::3.1.0"
  )

  override def sources = T.sources(
    super.sources() ++ Seq(
      PathRef(os.pwd / "tupson" / "shared" / "src" / "main" / "scala")
    )
  )

  object test extends Tests with TestModule.Munit {
    def ivyDeps = Agg(
      ivy"org.scalameta::munit::0.7.29"
    )
    override def sources = T.sources(
      super.sources() ++ Seq(
        PathRef(os.pwd / "tupson" / "shared" / "src" / "test" / "scala")
      )
    )
  }

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
