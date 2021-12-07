inThisBuild(
  List(
    organization := "ba.sake",
    homepage := Some(url("https://github.com/sake92/tupson")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/sake92/tupson"),
        "scm:git:git@https://github.com/sake92/tupson.git"
      )
    ),
    developers := List(
      Developer(
        "sake92",
        "Sakib Hadžiavdić",
        "sakib@sake.ba",
        url("https://sake.ba")
      )
    )
  )
)

lazy val tupson = crossProject(JVMPlatform, JSPlatform)
  .in(file("core"))
  .settings(
    name := "tupson",
    scalaVersion := "3.1.0",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "jawn-ast" % "1.3.0",
      "org.typelevel" %%% "shapeless3-deriving" % "3.0.4",
      "org.scalameta" %%% "munit" % "0.7.29" % Test
    )
  )
