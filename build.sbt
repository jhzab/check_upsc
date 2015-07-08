name := """upsc-nagios"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.7"

scalacOptions ++= Seq(
  "-Xlint",
  "-deprecation",
  "-Xfatal-warnings",
  "-feature"
)

libraryDependencies += "com.github.scopt" %% "scopt" % "3.3.0"

libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.1.3"

resolvers += Resolver.sonatypeRepo("public")
