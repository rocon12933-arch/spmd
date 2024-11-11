ThisBuild / scalaVersion     := "3.3.4"
ThisBuild / version          := "0.0.2"
ThisBuild / organization     := "moe.karla"



val zioVersion = "2.1.12"
val quillVersion = "4.8.6"
val zioHttpVersion = "3.0.1"
val zioConfigVersion = "4.0.2"
val zioLoggingVersion = "2.3.0"


assembly / assemblyJarName := "spmd-zio.jar"

enablePlugins(JavaAppPackaging)

ThisBuild / assemblyMergeStrategy := {
  case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.contains("getquill")  => MergeStrategy.preferProject
  case PathList(ps @ _*) if ps.last == "io.netty.versions.properties"  => MergeStrategy.first
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}


lazy val root = (project in file("."))
  .settings(
    name := "spmd-zio",
    scalaVersion := "3.3.4",
    scalacOptions ++= Seq("-Ykind-projector:underscores", "-language:postfixOps"),
    javacOptions ++= Seq("-source", "17", "-target", "17"),
    libraryDependencies ++= Seq(

      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,

      //"io.getquill" %% "quill-jdbc" % quillVersion,
      "io.getquill" %% "quill-jdbc-zio" % quillVersion,
      

      "dev.zio" %% "zio-http" % zioHttpVersion,

      "dev.zio" %% "zio-config" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,

      "dev.zio" %% "zio-logging" % zioLoggingVersion,
      //"dev.zio" %% "zio-logging-slf4j2" % zioLoggingVersion,

      "dev.zio" %% "zio-json" % "0.7.3",
      
      "com.zaxxer" % "HikariCP" % "6.1.0",
      //"org.xerial" % "sqlite-jdbc" % "3.45.3.0",
      //"org.apache.derby" % "derby" % "10.15.2.0",
      "com.h2database" % "h2" % "2.3.232",
      "org.flywaydb" % "flyway-core" % "10.21.0",
      "org.apache.commons" % "commons-compress" % "1.27.1",
      "com.github.junrar" % "junrar" % "7.5.5",
      //"org.flywaydb" % "flyway-core" % "9.22.3",
      //"com.microsoft.playwright" % "playwright" % "1.44.0",
      "org.slf4j" % "slf4j-simple" % "2.0.16",
      "org.jsoup" % "jsoup" % "1.18.1",
    ),
    assembly / mainClass := Some("moe.karla.AppMain")
  )
