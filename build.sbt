ThisBuild / organization := "com.github.foelock"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / name := "cloudburst"


libraryDependencies += "com.softwaremill.sttp.client" %% "core" % "2.0.0-RC11"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-derivation"
).map(_ % "0.12.0-M4")

libraryDependencies += "org.scalafx" %% "scalafx" % "8.0.192-R14"