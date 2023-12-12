ThisBuild / scalaVersion     := "3.3.1"
ThisBuild / version          := "0.0.1"
ThisBuild / organization     := "moe.karla"



val zioVersion = "2.0.19"
val quillVersion = "4.8.0"
val zioHttpVersion = "3.0.0-RC3"
val zioConfigVersion = "4.0.0-RC16"



assembly / assemblyJarName := "Spmd-zio_0.0.1.jar"
ThisBuild / assemblyMergeStrategy := {
  case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

lazy val root = (project in file("."))
  .settings(
    name := "spmd",
    scalaVersion := "3.3.1",
    scalacOptions ++= Seq("-Ykind-projector:underscores", "-language:postfixOps"),
    javacOptions ++= Seq("-source", "11", "-target", "11"),
    libraryDependencies ++= Seq(

      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,

      "io.getquill" %% "quill-jdbc" % quillVersion,
      "io.getquill" %% "quill-jdbc-zio" % quillVersion,

      "dev.zio" %% "zio-http" % zioHttpVersion,

      "dev.zio" %% "zio-config" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,

      "net.sourceforge.htmlunit" % "htmlunit" % "2.70.0",
      "org.slf4j" % "slf4j-simple" % "2.0.9",
      "org.jsoup" % "jsoup" % "1.17.1",
    ),
    assembly / mainClass := Some("moe.karla.Spmd")
  )
