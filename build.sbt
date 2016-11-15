name := "Marketplace"

version := "1.0"

scalaVersion := "2.11.8"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV = "2.4.12"
  val sprayV = "1.3.2"
  val specs2V = "2.4.17"
  val scalikejdbcV = "2.5.0"
  val slf4jV = "1.7.21"

  Seq(
    "io.spray"            %%  "spray-servlet" % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %%  "spray-json"    % sprayV,
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    "com.typesafe.akka"   %%  "akka-slf4j"    % akkaV,
    "org.specs2"          %%  "specs2-core"   % specs2V % "test",
    "org.scalikejdbc"     %%  "scalikejdbc"   % scalikejdbcV,
    "org.slf4j"           %   "slf4j-simple"  % slf4jV
  )
}

tomcat()