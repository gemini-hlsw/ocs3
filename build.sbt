import Settings.Libraries._
import Settings.LibraryVersions
import Settings.Plugins
import Common._
import AppsCommon._
import sbt.Keys._
import NativePackagerHelper._
import sbtcrossproject.crossProject
import sbtcrossproject.CrossType
import com.typesafe.sbt.packager.docker._

name := "seqexec"

Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / Compile / packageDoc / publishArtifact := false

// Gemini repository
ThisBuild / resolvers += "Gemini Repository".at(
  "https://github.com/gemini-hlsw/maven-repo/raw/master/releases"
)

// This key is used to find the JRE dir. It could/should be overriden on a user basis
// Add e.g. a `jres.sbt` file with your particular configuration
ThisBuild / ocsJreDir := Path.userHome / ".jres8_ocs3"

Global / cancelable := true

// Should make CI builds more robust
concurrentRestrictions in Global += Tags.limit(ScalaJSTags.Link, 2)

// Uncomment for local gmp testing
// resolvers in ThisBuild += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

// Settings to use git to define the version of the project
def versionFmt(out: sbtdynver.GitDescribeOutput): String = {
  val dirtySuffix = if (out.dirtySuffix.mkString("", "").nonEmpty) {
    "-UNCOMMITED"
  } else {
    ""
  }
  s"-${out.commitSuffix.sha}$dirtySuffix"
}

def fallbackVersion(d: java.util.Date): String = s"HEAD-${sbtdynver.DynVer.timestamp(d)}"

val dateFormatter = java.time.format.DateTimeFormatter.BASIC_ISO_DATE

inThisBuild(
  List(
    version := dateFormatter.format(
      dynverCurrentDate.value.toInstant.atZone(java.time.ZoneId.of("UTC")).toLocalDate
    ) + dynverGitDescribeOutput.value.mkVersion(versionFmt,
                                                fallbackVersion(dynverCurrentDate.value)
    )
  )
)

enablePlugins(GitBranchPrompt)

// Custom commands to facilitate web development
val startSeqexecAllCommands   = List(
  "seqexec_web_server/reStart",
  "seqexec_web_client/fastOptJS::startWebpackDevServer",
  "~seqexec_web_client/fastOptJS"
)
val restartSeqexecWDSCommands = List(
  "seqexec_web_client/fastOptJS::stopWebpackDevServer",
  "seqexec_web_client/fastOptJS::startWebpackDevServer",
  "~seqexec_web_client/fastOptJS"
)
val stopSeqexecAllCommands    = List(
  "seqexec_web_server/reStop",
  "seqexec_web_client/fastOptJS::stopWebpackDevServer"
)

addCommandAlias("startSeqexecAll", startSeqexecAllCommands.mkString(";", ";", ""))
addCommandAlias("restartSeqexecWDS", restartSeqexecWDSCommands.mkString(";", ";", ""))
addCommandAlias("stopSeqexecAll", stopSeqexecAllCommands.mkString(";", ";", ""))

ThisBuild / resolvers +=
  Resolver.sonatypeRepo("snapshots")

ThisBuild / updateOptions := updateOptions.value.withLatestSnapshots(false)

publish / skip := true

//////////////
// Projects
//////////////

lazy val giapi = project
  .in(file("modules/giapi"))
  .enablePlugins(GitBranchPrompt)
  .settings(commonSettings: _*)
  .settings(
    addCompilerPlugin(Plugins.kindProjectorPlugin),
    libraryDependencies ++= Seq(Cats.value,
                                Mouse.value,
                                Shapeless.value,
                                CatsEffect.value,
                                Fs2,
                                GiapiJmsUtil,
                                GiapiJmsProvider,
                                GiapiStatusService,
                                Giapi,
                                GiapiCommandsClient
    ) ++ Logging.value ++ Monocle.value,
    libraryDependencies ++= Seq(GmpStatusGateway  % "test",
                                GmpStatusDatabase % "test",
                                GmpCmdJmsBridge   % "test",
                                NopSlf4j          % "test"
    )
  )

lazy val ocs2_api = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/ocs2_api"))
  .settings(commonSettings)
  .settings(
    name := "ocs2-api",
    libraryDependencies ++= Seq(CatsTime.value) ++
      LucumaCore.value
  )
  .dependsOn(seqexec_model)

// Project for the server side application
lazy val seqexec_web_server = project
  .in(file("modules/web/server"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(GitBranchPrompt)
  .settings(commonSettings: _*)
  .settings(
    addCompilerPlugin(Plugins.kindProjectorPlugin),
    libraryDependencies ++= Seq(UnboundId,
                                JwtCore,
                                JwtCirce,
                                Http4sPrometheus,
                                CommonsHttp,
                                ScalaMock,
                                Log4CatsNoop.value
    ) ++
      Http4sClient ++ Http4s ++ PureConfig ++ Logging.value,
    // Supports launching the server in the background
    reStart / javaOptions += s"-javaagent:${(ThisBuild / baseDirectory).value}/app/seqexec-server/src/universal/bin/jmx_prometheus_javaagent-0.3.1.jar=6060:${(ThisBuild / baseDirectory).value}/app/seqexec-server/src/universal/bin/prometheus.yaml",
    reStart / mainClass := Some("seqexec.web.server.http4s.WebServerLauncher")
  )
  .settings(
    buildInfoUsePackageAsPath := true,
    buildInfoKeys ++= Seq[BuildInfoKey](name, version, buildInfoBuildNumber),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoObject := "OcsBuildInfo",
    buildInfoPackage := "seqexec.web.server"
  )
  .dependsOn(seqexec_server)
  .dependsOn(seqexec_model.jvm % "compile->compile;test->test")

lazy val seqexec_web_client = project
  .in(file("modules/web/client"))
  .enablePlugins(ScalaJSPlugin)
  .enablePlugins(ScalaJSBundlerPlugin)
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(GitBranchPrompt)
  .disablePlugins(RevolverPlugin)
  .settings(lucumaScalaJsSettings: _*)
  .settings(
    // Needed for Monocle macros
    scalacOptions += "-Ymacro-annotations",
    scalacOptions ~= (_.filterNot(
      Set(
        // By necessity facades will have unused params
        "-Wunused:params",
        "-Wunused:explicits"
      )
    )),
    // Configurations for webpack
    fastOptJS / webpackBundlingMode := BundlingMode.LibraryOnly(),
    fullOptJS / webpackBundlingMode := BundlingMode.Application,
    webpackResources := (baseDirectory.value / "src" / "webpack") * "*.js",
    webpackDevServerPort := 9090,
    webpack / version := "4.44.1",
    startWebpackDevServer / version := "3.11.0",
    // Use a different Webpack configuration file for production and create a single bundle without source maps
    fullOptJS / webpackConfigFile := Some(
      baseDirectory.value / "src" / "webpack" / "prod.webpack.config.js"
    ),
    fastOptJS / webpackConfigFile := Some(
      baseDirectory.value / "src" / "webpack" / "dev.webpack.config.js"
    ),
    Test / webpackConfigFile := Some(
      baseDirectory.value / "src" / "webpack" / "test.webpack.config.js"
    ),
    webpackEmitSourceMaps := false,
    Test / parallelExecution := false,
    installJsdom / version := "16.4.0",
    Test / requireJsDomEnv := true,
    // Use yarn as it is faster than npm
    useYarn := true,
    // JS dependencies via npm
    Compile / npmDependencies ++= Seq(
      "fomantic-ui-less" -> LibraryVersions.fomanticUI,
      "prop-types"       -> "15.7.2",
      "core-js"          -> "2.6.11" // Without this, core-js 3 is used, which conflicts with @babel/runtime-corejs2
    ),
    Compile / fastOptJS / scalaJSLinkerConfig ~= { _.withSourceMap(false) },
    Compile / fullOptJS / scalaJSLinkerConfig ~= { _.withSourceMap(false) },
    // NPM libs for development, mostly to let webpack do its magic
    Compile / npmDevDependencies ++= Seq(
      "postcss"                       -> "8.1.1",
      "postcss-loader"                -> "4.0.3",
      "autoprefixer"                  -> "10.0.1",
      "url-loader"                    -> "4.1.0",
      "file-loader"                   -> "6.0.0",
      "css-loader"                    -> "3.5.3",
      "style-loader"                  -> "1.2.1",
      "less"                          -> "3.9.0",
      "less-loader"                   -> "7.0.1",
      "webpack-merge"                 -> "4.2.2",
      "mini-css-extract-plugin"       -> "0.8.0",
      "webpack-dev-server-status-bar" -> "1.1.0",
      "cssnano"                       -> "4.1.10",
      "terser-webpack-plugin"         -> "3.0.6",
      "html-webpack-plugin"           -> "4.3.0",
      "css-minimizer-webpack-plugin"  -> "1.1.5",
      "favicons-webpack-plugin"       -> "4.2.0",
      "@packtracker/webpack-plugin"   -> "2.3.0"
    ),
    libraryDependencies ++= Seq(
      Cats.value,
      Mouse.value,
      CatsEffect.value,
      ScalaJSDom.value,
      JavaTimeJS.value,
      ScalaJSReactSemanticUI.value,
      ScalaJSReactVirtualized.value,
      ScalaJSReactClipboard.value,
      ScalaJSReactSortable.value,
      ScalaJSReactDraggable.value,
      GeminiLocales.value,
      LucumaUI.value,
      PPrint.value,
      TestLibs.value
    ) ++ MUnit.value ++ ReactScalaJS.value ++ Diode.value ++ Log4CatsLogLevel.value
  )
  .settings(
    buildInfoUsePackageAsPath := true,
    buildInfoKeys ++= Seq[BuildInfoKey](name, version),
    buildInfoObject := "OcsBuildInfo",
    buildInfoPackage := "seqexec.web.client"
  )
  .dependsOn(seqexec_model.js % "compile->compile;test->test")

// List all the modules and their inter dependencies
lazy val seqexec_server     = project
  .in(file("modules/server"))
  .enablePlugins(GitBranchPrompt)
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings: _*)
  .settings(
    scalacOptions += "-Ymacro-annotations",
    addCompilerPlugin(Plugins.kindProjectorPlugin),
    addCompilerPlugin(Plugins.betterMonadicForPlugin),
    libraryDependencies ++=
      Seq(
        Http4sCirce,
        Squants.value,
        // OCS bundles
        SpModelCore,
        POT,
        OpenCSV,
        Http4sBoopickle,
        PrometheusClient,
        Log4Cats.value,
        Log4CatsNoop.value,
        TestLibs.value,
        PPrint.value
      ) ++ MUnit.value ++ Http4s ++ Http4sClient ++ PureConfig ++ SeqexecOdb ++ Monocle.value ++ WDBAClient ++
        Circe.value
  )
  .settings(
    buildInfoUsePackageAsPath := true,
    buildInfoKeys ++= Seq[BuildInfoKey](name, version),
    buildInfoObject := "OcsBuildInfo",
    buildInfoPackage := "seqexec.server"
  )
  .dependsOn(seqexec_engine    % "compile->compile;test->test",
             giapi,
             ocs2_api.jvm,
             seqexec_model.jvm % "compile->compile;test->test",
             acm               % "compile->compile;test->test"
  )

// Unfortunately crossProject doesn't seem to work properly at the module/build.sbt level
// We have to define the project properties at this level
lazy val seqexec_model = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/model"))
  .enablePlugins(GitBranchPrompt)
  .settings(
    scalacOptions += "-Ymacro-annotations",
    libraryDependencies ++= Seq(
      Squants.value,
      Mouse.value,
      BooPickle.value,
      CatsTime.value
    ) ++ MUnit.value ++ Monocle.value ++ LucumaCore.value
  )
  .jvmSettings(
    commonSettings,
    libraryDependencies += Http4sCore
  )
  .jsSettings(lucumaScalaJsSettings)
  .jsSettings(
    // And add a custom one
    libraryDependencies += JavaTimeJS.value,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
  )

lazy val seqexec_engine = project
  .in(file("modules/engine"))
  .enablePlugins(GitBranchPrompt)
  .dependsOn(seqexec_model.jvm % "compile->compile;test->test")
  .settings(commonSettings: _*)
  .settings(
    addCompilerPlugin(Plugins.kindProjectorPlugin),
    scalacOptions += "-Ymacro-annotations",
    libraryDependencies ++= Seq(Fs2,
                                CatsEffect.value,
                                Log4s.value,
                                Log4Cats.value
    ) ++ Monocle.value ++ MUnit.value
  )

lazy val acm = project
  .in(file("modules/acm"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      EpicsService,
      GmpCommandsRecords,
      Guava,
      Slf4j,
      XmlUnit,
      ScalaMock,
      JUnitInterface
    ) ++ Logback ++ JAXB,
    Test / libraryDependencies ++= Logback,
    Test / testOptions := Seq(),
    Compile / sourceGenerators += Def.task {
      import scala.sys.process._
      val pkg = "edu.gemini.epics.acm.generated"
      val log = state.value.log
      val gen = (Compile / sourceManaged).value
      val out = pkg.split("\\.").foldLeft(gen)(_ / _)
      val xsd = sourceDirectory.value / "main" / "resources" / "CaSchema.xsd"
      val cmd = List("xjc", "-d", gen.getAbsolutePath, "-p", pkg, xsd.getAbsolutePath)
      val mod = xsd.getParentFile.listFiles.map(_.lastModified).max
      val cur =
        if (out.exists && out.listFiles.nonEmpty) out.listFiles.map(_.lastModified).min
        else Int.MaxValue
      if (mod > cur) {
        out.mkdirs
        val err = cmd.run(ProcessLogger(log.info(_), log.error(_))).exitValue
        if (err != 0) sys.error("xjc failed")
      }
      out.listFiles.toSeq
    }.taskValue
  )

/**
 * Common settings for the Seqexec instances
 */
lazy val seqexecCommonSettings = Seq(
  // Main class for launching
  Compile / mainClass := Some("seqexec.web.server.http4s.WebServerLauncher"),
  // This is important to keep the file generation order correctly
  Universal / parallelExecution := false,
  // Depend on webpack and add the assets created by webpack
  Compile / packageBin / mappings ++= (webpack in (seqexec_web_client, Compile, fullOptJS)).value
    .map(f => f.data -> f.data.getName()),
  // Name of the launch script
  executableScriptName := "seqexec-server",
  // No javadocs
  mappings in (Compile, packageDoc) := Seq(),
  // Don't create launchers for Windows
  makeBatScripts := Seq.empty,
  // Specify a different name for the config file
  bashScriptConfigLocation := Some("${app_home}/../conf/launcher.args"),
  bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml"""",
  bashScriptExtraDefines += """addJava "-javaagent:${app_home}/jmx_prometheus_javaagent-0.3.1.jar=6060:${app_home}/prometheus.yaml"""",
  // Copy logback.xml to let users customize it on site
  Universal / mappings += {
    val f = (resourceDirectory in (seqexec_web_server, Compile)).value / "logback.xml"
    f -> ("conf/" + f.getName)
  },
  // Launch options
  Universal / javaOptions ++= Seq(
    // -J params will be added as jvm parameters
    "-J-Xmx1024m",
    "-J-Xms256m",
    // Support remote JMX access
    "-J-Dcom.sun.management.jmxremote",
    "-J-Dcom.sun.management.jmxremote.authenticate=false",
    "-J-Dcom.sun.management.jmxremote.port=2407",
    "-J-Dcom.sun.management.jmxremote.ssl=false",
    // Ensure the local is correctly set
    "-J-Duser.language=en",
    "-J-Duser.country=US",
    // Support remote debugging
    "-J-Xdebug",
    "-J-Xnoagent",
    "-J-XX:+HeapDumpOnOutOfMemoryError",
    // Make sure the application exits on OOM
    "-J-XX:+ExitOnOutOfMemoryError",
    "-J-XX:+CrashOnOutOfMemoryError",
    "-J-XX:HeapDumpPath=/tmp",
    "-J-Xrunjdwp:transport=dt_socket,address=8457,server=y,suspend=n",
    "-java-home ${app_home}/../jre" // This breaks builds without jre
  )
) ++ commonSettings

/**
 * Settings for Seqexec in Linux
 */
lazy val seqexecLinux = Seq(
  // User/Group for execution
  Linux / daemonUser := "software",
  Linux / daemonGroup := "software",
  Universal / maintainer := "Software Group <software@gemini.edu>",
  // This lets us build RPMs from snapshot versions
  Linux / name := "Seqexec Server",
  Linux / version := {
    (ThisBuild / version).value.replace("-SNAPSHOT", "").replace("-", "_").replace(" ", "")
  }
)

/**
 * Project for the seqexec server app for development
 */
lazy val app_seqexec_server = preventPublication(project.in(file("app/seqexec-server")))
  .dependsOn(seqexec_web_server, seqexec_web_client)
  .aggregate(seqexec_web_server, seqexec_web_client)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(GitBranchPrompt)
  .settings(seqexecCommonSettings: _*)
  .settings(
    description := "Seqexec server for local testing",
    // Put the jar files in the lib dir
    Universal / mappings += {
      val jar = (Compile / packageBin).value
      jar -> ("lib/" + jar.getName)
    },
    Universal / mappings := {
      // filter out sjs jar files. otherwise it could generate some conflicts
      val universalMappings = (Universal / mappings).value
      val filtered          = universalMappings.filter { case (_, name) =>
        !name.contains("_sjs")
      }
      filtered
    },
    Universal / mappings += {
      val f = (Compile / resourceDirectory).value / "update_smartgcal"
      f -> ("bin/" + f.getName)
    },
    Universal / mappings += {
      val f = (Compile / resourceDirectory).value / "seqexec-server.env"
      f -> ("systemd/" + f.getName)
    },
    Universal / mappings += {
      val f = (Compile / resourceDirectory).value / "seqexec-server.service"
      f -> ("systemd/" + f.getName)
    }
  )

/**
 * Project for the seqexec test server at GS on Linux 64
 */
lazy val app_seqexec_server_gs_test =
  preventPublication(project.in(file("app/seqexec-server-gs-test")))
    .dependsOn(seqexec_web_server, seqexec_web_client)
    .aggregate(seqexec_web_server, seqexec_web_client)
    .enablePlugins(LinuxPlugin)
    .enablePlugins(JavaServerAppPackaging)
    .enablePlugins(SystemdPlugin)
    .enablePlugins(GitBranchPrompt)
    .settings(seqexecCommonSettings: _*)
    .settings(seqexecLinux: _*)
    .settings(deployedAppMappings: _*)
    .settings(
      description := "Seqexec GS test deployment",
      applicationConfName := "seqexec",
      applicationConfSite := DeploymentSite.GS,
      Universal / mappings := {
        // filter out sjs jar files. otherwise it could generate some conflicts
        val universalMappings = (mappings in (app_seqexec_server, Universal)).value
        val filtered          = universalMappings.filter { case (_, name) =>
          !name.contains("_sjs")
        }
        filtered
      }
    )
    .settings(embeddedJreSettingsLinux64: _*)
    .dependsOn(seqexec_server)

/**
 * Project for the seqexec test server at GN on Linux 64
 */
lazy val app_seqexec_server_gn_test =
  preventPublication(project.in(file("app/seqexec-server-gn-test")))
    .dependsOn(seqexec_web_server, seqexec_web_client)
    .aggregate(seqexec_web_server, seqexec_web_client)
    .enablePlugins(LinuxPlugin, RpmPlugin)
    .enablePlugins(JavaServerAppPackaging)
    .enablePlugins(GitBranchPrompt)
    .settings(seqexecCommonSettings: _*)
    .settings(seqexecLinux: _*)
    .settings(deployedAppMappings: _*)
    .settings(
      description := "Seqexec GN test deployment",
      applicationConfName := "seqexec",
      applicationConfSite := DeploymentSite.GN,
      Universal / mappings := {
        // filter out sjs jar files. otherwise it could generate some conflicts
        val universalMappings = (mappings in (app_seqexec_server, Universal)).value
        val filtered          = universalMappings.filter { case (_, name) =>
          !name.contains("_sjs")
        }
        filtered
      }
    )
    .settings(embeddedJreSettingsLinux64: _*)
    .dependsOn(seqexec_server)

/**
 * Project for the seqexec server app for production on Linux 64
 */
lazy val app_seqexec_server_gs = preventPublication(project.in(file("app/seqexec-server-gs")))
  .dependsOn(seqexec_web_server, seqexec_web_client)
  .aggregate(seqexec_web_server, seqexec_web_client)
  .enablePlugins(LinuxPlugin, RpmPlugin)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(GitBranchPrompt)
  .settings(seqexecCommonSettings: _*)
  .settings(seqexecLinux: _*)
  .settings(deployedAppMappings: _*)
  .settings(
    description := "Seqexec Gemini South server production",
    applicationConfName := "seqexec",
    applicationConfSite := DeploymentSite.GS,
    Universal / mappings := {
      // filter out sjs jar files. otherwise it could generate some conflicts
      val universalMappings = (mappings in (app_seqexec_server, Universal)).value
      val filtered          = universalMappings.filter { case (_, name) =>
        !name.contains("_sjs")
      }
      filtered
    }
  )
  .settings(embeddedJreSettingsLinux64: _*)
  .dependsOn(seqexec_server)

/**
 * Project for the GN seqexec server app for production on Linux 64
 */
lazy val app_seqexec_server_gn = preventPublication(project.in(file("app/seqexec-server-gn")))
  .dependsOn(seqexec_web_server, seqexec_web_client)
  .aggregate(seqexec_web_server, seqexec_web_client)
  .enablePlugins(LinuxPlugin, RpmPlugin)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(GitBranchPrompt)
  .settings(seqexecCommonSettings: _*)
  .settings(seqexecLinux: _*)
  .settings(deployedAppMappings: _*)
  .settings(
    description := "Seqexec Gemini North server production",
    applicationConfName := "seqexec",
    applicationConfSite := DeploymentSite.GN,
    Universal / mappings := {
      // filter out sjs jar files. otherwise it could generate some conflicts
      val universalMappings = (mappings in (app_seqexec_server, Universal)).value
      val filtered          = universalMappings.filter { case (_, name) =>
        !name.contains("_sjs")
      }
      filtered
    }
  )
  .settings(embeddedJreSettingsLinux64: _*)
  .dependsOn(seqexec_server)
