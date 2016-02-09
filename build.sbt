import com.github.retronym.SbtOneJar._

oneJarSettings

name := "data-scripting"

version := "0.0.1"

scalaVersion := "2.11.7"

parallelExecution in Test := false

resolvers ++= Seq(
  "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases",
  "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"
)

libraryDependencies += "org.tpolecat" %% "doobie-core" % "0.2.3"

libraryDependencies += "org.tpolecat" %% "doobie-contrib-hikari" % "0.2.3"

libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.20"

libraryDependencies += "io.argonaut" %% "argonaut" % "6.1-M4"

libraryDependencies += "net.databinder.dispatch" %% "dispatch-core" % "0.11.2"

libraryDependencies += "joda-time" % "joda-time" % "2.6"

libraryDependencies += "com.couchbase.client" % "java-client" % "2.0.1"

libraryDependencies +=  "com.github.scopt"             %% "scopt"                   % "3.3.0"

autoCompilerPlugins := true

scalacOptions += "-feature"
