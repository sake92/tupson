
lazy val core = project
  .in(file("core"))
  .settings(
    organization := "ba.sake",
    name := "tupson",
    version := "0.1.1-SNAPSHOT",
    scalaVersion := "3.1.0",
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
  )
