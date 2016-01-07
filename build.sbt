name := """check_upsc"""

version := "1.0"

scalaVersion := "2.11.7"

scalacOptions ++= Seq(
  "-Xlint",
  "-deprecation",
  "-Xfatal-warnings",
  "-feature"
)

libraryDependencies += "com.github.scopt" %% "scopt" % "3.3.0"

libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.1.5"

libraryDependencies += "org.parboiled" %% "parboiled" % "2.1.0"

resolvers += Resolver.sonatypeRepo("public")
