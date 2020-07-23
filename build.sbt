import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport._

scalaVersion := "2.13.2"
name := "wdlTools"
import com.typesafe.config._
val confPath =
  Option(System.getProperty("config.file")).getOrElse("src/main/resources/application.conf")
val conf = ConfigFactory.parseFile(new File(confPath)).resolve()
version := conf.getString("wdlTools.version")
organization := "com.dnanexus"
developers := List(
    Developer("orodeh", "orodeh", "orodeh@dnanexus.com", url("https://github.com/dnanexus-rnd")),
    Developer("jdidion", "jdidion", "jdidion@dnanexus.com", url("https://github.com/dnanexus-rnd"))
)
homepage := Some(url("https://github.com/dnanexus-rnd/wdlTools"))
scmInfo := Some(
    ScmInfo(url("https://github.com/dnanexus-rnd/wdlTools"),
            "git@github.com:dnanexus-rnd/wdlTools.git")
)
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
publishMavenStyle := true

val root = project.in(file("."))

// disable publish with scala version, otherwise artifact name will include scala version
// e.g wdlTools_2.11
crossPaths := false

// add sonatype repository settings
// snapshot versions publish to sonatype snapshot repository
// other versions publish to sonatype staging repository
publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
)

// reduce the maximum number of errors shown by the Scala compiler
maxErrors := 20

//coverageEnabled := true

javacOptions ++= Seq("-Xlint:deprecation")

// Show deprecation warnings
scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-explaintypes",
    "-encoding",
    "UTF-8",
    "-Xlint:constant",
    "-Xlint:delayedinit-select",
    "-Xlint:doc-detached",
    "-Xlint:inaccessible",
    "-Xlint:infer-any",
    "-Xlint:nullary-override",
    "-Xlint:nullary-unit",
    "-Xlint:option-implicit",
    "-Xlint:package-object-classes",
    "-Xlint:poly-implicit-overload",
    "-Xlint:private-shadow",
    "-Xlint:stars-align",
    "-Xlint:type-parameter-shadow",
    "-Ywarn-dead-code",
    "-Ywarn-unused:implicits",
    "-Ywarn-unused:privates",
    "-Ywarn-unused:locals",
    "-Ywarn-unused:imports", // warns about every unused import on every command.
    "-Xfatal-warnings" // makes those warnings fatal.
)

assemblyJarName in assembly := "wdlTools.jar"
logLevel in assembly := Level.Info
//assemblyOutputPath in assembly := file("applet_resources/resources/dxWDL.jar")

val antlr4Version = "4.8"
val scallopVersion = "3.4.0"
val typesafeVersion = "1.3.3"
val scalateVersion = "1.9.6"
val sprayVersion = "1.3.5"
val katanVersion = "0.6.1"
val graphVersion = "1.13.2"
val scalatestVersion = "3.1.1"

libraryDependencies ++= Seq(
    // antlr4 lexer + parser
    "org.antlr" % "antlr4" % antlr4Version,
    // command line parser
    "org.rogach" %% "scallop" % scallopVersion,
    // template engine
    "org.scalatra.scalate" %% "scalate-core" % scalateVersion,
    "com.typesafe" % "config" % typesafeVersion,
    "io.spray" %% "spray-json" % sprayVersion,
    "com.nrinaudo" %% "kantan.csv" % katanVersion,
    "org.scala-graph" %% "graph-core" % graphVersion,
    //---------- Test libraries -------------------//
    "org.scalatest" % "scalatest_2.13" % scalatestVersion % "test"
)

// If an exception is thrown during tests, show the full
// stack trace, by adding the "-oF" option to the list.
//

// exclude the native tests, they are slow.
// to do this from the command line:
// sbt testOnly -- -l native
//
// comment out this line in order to allow native
// tests
// Test / testOptions += Tests.Argument("-l", "native")
Test / testOptions += Tests.Argument("-oF")

Test / parallelExecution := false

// comment out this line to enable tests in assembly
test in assembly := {}

// scalafmt
scalafmtConfig := root.base / ".scalafmt.conf"
// Coverage
//
// sbt clean coverage test
// sbt coverageReport

// To turn it off do:
// sbt coverageOff

// Ignore code parts that cannot be checked in the unit
// test environment
//coverageExcludedPackages := "dxWDL.Main;dxWDL.compiler.DxNI;dxWDL.compiler.DxObjectDirectory;dxWDL.compiler.Native"

// The Java code generated by Antlr causes errors during API doc generation. Right now we disable
// generating any API docs, but eventually we should selectively exclude just the Java sources.
//publishArtifact in (Compile, packageDoc) := false

// exclude Java sources from scaladoc
scalacOptions in (Compile, doc) ++= Seq("-no-java-comments", "-no-link-warnings")
