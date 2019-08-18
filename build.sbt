ThisBuild / organization := "com.github.foelock"
ThisBuild / scalaVersion := "2.13.0"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / name := "cloudburst"


libraryDependencies ++= Seq(
  "com.softwaremill.sttp" %% "core" % "1.6.4"
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-derivation"
).map(_ % "0.12.0-M4")
