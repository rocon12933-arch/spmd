ThisBuild / scalaVersion     := "3.4.1"
ThisBuild / version          := "0.0.1"
ThisBuild / organization     := "moe.karla"



val zioVersion = "2.1.1"
val quillVersion = "4.8.4"
val zioHttpVersion = "3.0.0-RC6"
val zioConfigVersion = "4.0.2"
val zioLoggingVersion = "2.2.3"


assembly / assemblyJarName := "Spmd-zio_0.0.1.jar"


ThisBuild / assemblyMergeStrategy := {
  case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

lazy val root = (project in file("."))
  .settings(
    name := "spmd-zio",
    scalaVersion := "3.4.1",
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

      "dev.zio" %% "zio-json" % "0.6.2",
      
      "com.zaxxer" % "HikariCP" % "5.1.0",
      "org.xerial" % "sqlite-jdbc" % "3.45.3.0",
      //"org.apache.derby" % "derby" % "10.15.2.0",
      "com.h2database" % "h2" % "2.2.224",
      "org.flywaydb" % "flyway-core" % "10.10.0",
      //"org.flywaydb" % "flyway-core" % "9.22.3",
      "com.microsoft.playwright" % "playwright" % "1.43.0",
      "org.slf4j" % "slf4j-simple" % "2.0.13",
      "org.jsoup" % "jsoup" % "1.17.2",
    ),
    assembly / mainClass := Some("moe.karla.AppMain")
  )
