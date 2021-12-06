lazy val core = crossProject(JVMPlatform, JSPlatform)
  .in(file("core"))
  .settings(
    organization := "ba.sake",
    name := "tupson",
    scalaVersion := "3.1.0",
    libraryDependencies += "org.scalameta" %%% "munit" % "0.7.29" % Test
  )
  .settings(
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
