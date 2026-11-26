ThisBuild / scalaVersion := "3.9.0"

lazy val root = (project in file("."))
  .enablePlugins(JmhPlugin)
  .settings(
    name := "tupson-jmh",
    Compile / unmanagedSourceDirectories ++= Seq(
      baseDirectory.value / ".." / "tupson" / "src",
      baseDirectory.value / ".." / "tupson" / "src-jvm"
    ),
    libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.43.0",
    scalacOptions ++= Seq(
      "-Yretain-trees",
      "-deprecation",
      "-Xcheck-macros"
    )
  )
