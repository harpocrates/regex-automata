ThisBuild / scalaVersion     := "2.13.7"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.atheriault"

lazy val root = (project in file("."))
  .settings(
    crossPaths := false, // drop off Scala suffix from artifact names.
    autoScalaLibrary := false,
    name := "regex-automata",
    javacOptions += "-Xlint",
    scalacOptions ++= Seq(
      "-encoding", "utf8",
      "-deprecation",
      "-unchecked",
      "-Xlint",
    ),
    libraryDependencies ++= Seq(
      "org.ow2.asm" % "asm" % "9.2",
      "org.scala-lang" % "scala-library" % scalaVersion.value % Test,
      "org.scalatest" %% "scalatest" % "3.2.10" % Test,
      "org.scalacheck" %% "scalacheck" % "1.15.4" % Test,
      "org.scalatestplus" %% "scalacheck-1-15" % "3.2.10.0" % Test,
    ),
  )

lazy val bench = (project in file("bench"))
  .dependsOn(root)
  .enablePlugins(JmhPlugin)

