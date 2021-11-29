ThisBuild / scalaVersion     := "2.13.7"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.atheriault"

lazy val root = (project in file("."))
  .settings(
    name := "regex-automata",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.9" % Test,
    libraryDependencies += "org.ow2.asm" % "asm" % "9.2"
  )
