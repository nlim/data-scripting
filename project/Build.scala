import sbt._
import Keys._

object BuildSettings {
  val buildScalaVersion = "2.11.7"
  val buildVersion      = "0.0.1"

  val extraResolvers = Seq(
    "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
    "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases",
    "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"
  )

  val dependencies = Seq(
    "org.tpolecat" %% "doobie-core" % "0.2.3",
    "org.tpolecat" %% "doobie-contrib-hikari" % "0.2.3",
    "mysql" % "mysql-connector-java" % "5.1.20",
    "io.argonaut" %% "argonaut" % "6.1-M4",
    "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
    "joda-time" % "joda-time" % "2.6",
    "com.couchbase.client" % "java-client" % "2.0.1",
    "com.github.scopt" %% "scopt" % "3.3.0"
  )


  val buildSettings = Defaults.defaultSettings ++ Seq(
    version      := buildVersion,
    scalaVersion := "2.11.7",
    exportJars := true,
    javacOptions ++= Seq("-Xlint:unchecked"),
    scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-encoding", "utf8"),
    resolvers ++= extraResolvers
  )
}

object Build extends Build {

  lazy val root = Project(
    "datascripting",
    file("."),
    settings = BuildSettings.buildSettings ++ Seq(
      libraryDependencies := BuildSettings.dependencies
    )
  )

  lazy val examples = Project(
    "datascripting-examples",
    file("examples"),
    settings = BuildSettings.buildSettings
  ) dependsOn (root)
}
