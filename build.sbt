name := "Marketplace"

version := "1.0"

scalaVersion := "2.11.8"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += Resolver.bintrayRepo("cakesolutions", "maven")

libraryDependencies ++= {
  val akkaV = "2.4.16"
  val sprayV = "1.3.3"
  val specs2V = "3.8.6"
  val scalikejdbcV = "2.5.0"
  val logbackV = "1.1.8"
  val kafkaV = "0.10.1.1"
  val scalaxmlV = "1.0.6"
  val commonsCodecV = "1.10"

  Seq(
    "io.spray"                %%  "spray-servlet"             % sprayV,
    "io.spray"                %%  "spray-routing"             % sprayV,
    "io.spray"                %%  "spray-json"                % sprayV,
    "io.spray"                %%  "spray-can"                 % sprayV,
    "io.spray"                %%  "spray-testkit"             % sprayV % "test" exclude("org.specs2", "specs2_2.11"),
    "com.typesafe.akka"       %%  "akka-actor"                % akkaV,
    "com.typesafe.akka"       %%  "akka-testkit"              % akkaV % "test",
    "com.typesafe.akka"       %%  "akka-slf4j"                % akkaV,
    "org.specs2"              %%  "specs2-core"               % specs2V % "test",
    "org.scalikejdbc"         %%  "scalikejdbc"               % scalikejdbcV,
    "org.scalikejdbc"         %%  "scalikejdbc-config"        % scalikejdbcV,
    "ch.qos.logback"          %   "logback-classic"           % logbackV,
    "net.cakesolutions"       %% "scala-kafka-client-akka"    % kafkaV,
    "org.scala-lang.modules"  %%  "scala-xml"                 % scalaxmlV,
    "commons-codec"           %  "commons-codec"              % commonsCodecV
  )
}

parallelExecution in Test := false

enablePlugins(TomcatPlugin)
