import fs2.grpc.buildinfo.BuildInfo

val scalaVersion_     = "3.2.0"
val http4sVersion     = "0.23.14"
val configVersion     = "1.3.1"
val catsEffectVersion = "3.4.8"
val scalatestVersion  = "3.2.15"
val circeVersion      = "0.14.4"
val pureConfigVersion = "0.17.2"
val scalaLogVersion   = "3.9.5"
val slf4jVersion      = "2.0.7"

ThisBuild / scalaVersion := scalaVersion_

lazy val commonSettings =
  Seq(
    organization := "org.bitlap",
    scalaVersion := scalaVersion_,
    scalacOptions ++= Seq(
      "-unchecked",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-deprecation"
    )
  )

lazy val assemblySettings = Seq(
  assembly / assemblyJarName := name.value + ".jar",
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case "application.conf"            => MergeStrategy.concat
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  }
)

lazy val weather = project
  .in(file("."))
  .settings(commonSettings)
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `weather-protobuf`,
    `weather-client`,
    `weather-server`
  )

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")

lazy val `weather-protobuf` = project
  .in(file("weather-protobuf"))
  .settings(commonSettings)
  .settings(
    name := "weather-protobuf",
//    Compile / PB.targets := Seq(
//      scalapb.gen(grpc = true) -> (Compile / sourceManaged).value, {
//        protocbridge.SandboxedJvmGenerator.forModule(
//          "scala-fs2-grpc",
//          protocbridge.Artifact(
//            BuildInfo.organization,
//            s"${BuildInfo.codeGeneratorName}_${CrossVersion.binaryScalaVersion(BuildInfo.scalaVersion)}",
//            BuildInfo.version
//          ),
//          BuildInfo.codeGeneratorFullName + "$",
//          Nil
//        )
//      } -> (Compile / sourceManaged).value / "fs2-grpc"
//    ),
    libraryDependencies ++= Seq(
      "io.grpc"               % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime"   % scalapb.compiler.Version.scalapbVersion % "protobuf"
    )
  )
  .enablePlugins(Fs2Grpc)
  .disablePlugins(AssemblyPlugin)

lazy val `weather-client` = project
  .in(file("weather-client"))
  .settings(
    name := "weather-client",
    commonSettings,
    assemblySettings,
    libraryDependencies ++= Seq(
      "com.typesafe"   % "config"      % configVersion withSources (),
      "org.typelevel" %% "cats-effect" % catsEffectVersion withSources ()
    )
  )
  .dependsOn(
    `weather-protobuf`
  )

lazy val `weather-server` = project
  .in(file("weather-server"))
  .settings(
    name := "weather-server",
    commonSettings,
    assemblySettings,
    libraryDependencies ++= Seq(
      "com.typesafe"           % "config"              % configVersion withSources (),
      "org.typelevel"         %% "cats-effect"         % catsEffectVersion withSources (),
      "com.github.pureconfig" %% "pureconfig-core"     % pureConfigVersion withSources (),
      "org.http4s"            %% "http4s-dsl"          % http4sVersion withSources (),
      "org.http4s"            %% "http4s-blaze-client" % http4sVersion withSources (),
      "org.http4s"            %% "http4s-blaze-server" % http4sVersion withSources (),
      "org.http4s"            %% "http4s-circe"        % http4sVersion withSources (),
      "io.circe"              %% "circe-parser"        % circeVersion withSources (),
      "io.circe"              %% "circe-generic"       % circeVersion withSources (),
      "io.circe"              %% "circe-core"          % circeVersion withSources (),
      "org.slf4j"              % "slf4j-api"           % slf4jVersion withSources (),
      "org.slf4j"              % "slf4j-simple"        % slf4jVersion withSources ()
    )
  )
  .dependsOn(
    `weather-protobuf`
  )
