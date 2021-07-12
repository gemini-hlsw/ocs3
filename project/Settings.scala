import sbt._
import java.lang.{ Runtime => JRuntime }
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

/**
  * Application settings and dependencies
  */
object Settings {

  /** Library versions */
  object LibraryVersions {
    // ScalaJS libraries
    val scalaDom                = "1.1.0"
    val scalajsReact            = "1.7.7"
    val booPickle               = "1.3.3"
    val diode                   = "1.1.14"
    val javaTimeJS              = "2.3.0"
    val scalaJSReactCommon      = "0.11.3"
    val scalaJSSemanticUI       = "0.10.8"
    val scalaJSReactVirtualized = "0.11.3"
    val scalaJSReactClipboard   = "1.5.0"
    val scalaJSReactDraggable   = "0.11.3"
    val scalaJSReactSortable    = "0.4.2"

    // Scala libraries
    val catsEffectVersion   = "2.5.1"
    val catsVersion         = "2.6.1"
    val mouseVersion        = "1.0.4"
    val fs2Version          = "2.5.6"
    val shapelessVersion    = "2.3.7"
    val scalaParsersVersion = "1.1.2"
    val scalaXmlVerson      = "1.2.0"
    val catsTime            = "0.3.4"

    val http4sVersion  = "0.21.24"
    val squants        = "1.8.0"
    val commonsHttp    = "2.0.2"
    val unboundId      = "3.2.1"
    val jwt            = "5.0.0"
    val slf4j          = "1.7.31"
    val log4s          = "1.10.0"
    val log4cats       = "1.3.1"
    val log4catsLevel  = "0.2.0"
    val logback        = "1.2.3"
    val janino         = "3.1.4"
    val logstash       = "6.6"
    val pureConfig     = "0.14.1"
    val monocleVersion = "2.1.0"
    val circeVersion   = "0.14.1"
    val doobieVersion  = "0.6.0"
    val flywayVersion  = "6.0.4"

    // test libraries
    val xmlUnit                     = "1.6"
    val jUnitInterface              = "0.11"
    val scalaMock                   = "5.1.0"
    lazy val munitVersion           = "0.7.27"
    lazy val munitDisciplineVersion = "1.0.9"
    lazy val munitCatsEffectVersion = "0.3.0"

    // Pure JS libraries
    val fomanticUI = "2.8.7"
    val ocsVersion = "2021101.2.2"

    val apacheXMLRPC        = "3.1.3"
    val opencsv             = "2.1"
    val epicsService        = "1.0.7"
    val gmpCommandRecords   = "0.7.7"
    val giapi               = "1.1.7"
    val giapiJmsUtil        = "0.5.7"
    val giapiJmsProvider    = "1.6.7"
    val giapiCommandsClient = "0.2.7"
    val giapiStatusService  = "0.6.7"
    val gmpStatusGateway    = "0.3.7"
    val gmpStatusDatabase   = "0.3.7"
    val gmpCmdClientBridge  = "0.6.7"
    val guava               = "30.1.1-jre"
    val prometheusClient    = "0.11.0"
    val geminiLocales       = "0.6.0"
    val pprint              = "0.6.6"
    val jaxb                = "2.3.1"

    // Gemini Libraries
    val lucumaCore = "0.10.1"
    val lucumaUI   = "0.12.2"
  }

  /**
    * Global libraries
    */
  object Libraries {
    // Test Libraries
    val TestLibs       = Def.setting(
      "org.typelevel" %%% "cats-testkit-scalatest" % "2.1.5" % "test"
    )
    val MUnit          = Def.setting(
      Seq(
        "org.scalameta" %%% "munit"             % LibraryVersions.munitVersion           % Test,
        "org.typelevel" %%% "munit-cats-effect" % LibraryVersions.munitCatsEffectVersion % Test,
        "org.typelevel" %%% "discipline-munit"  % LibraryVersions.munitDisciplineVersion % Test
      )
    )
    val XmlUnit        = "xmlunit" % "xmlunit" % LibraryVersions.xmlUnit % "test"
    val JUnitInterface =
      "com.novocode" % "junit-interface" % LibraryVersions.jUnitInterface % "test"
    val ScalaMock   = "org.scalamock"     %% "scalamock"          % LibraryVersions.scalaMock  % "test"
    // Server side libraries
    val Cats        = Def.setting("org.typelevel" %%% "cats-core" % LibraryVersions.catsVersion)
    val CatsEffect  =
      Def.setting("org.typelevel" %%% "cats-effect" % LibraryVersions.catsEffectVersion)
    val Fs2         = "co.fs2"            %% "fs2-core"           % LibraryVersions.fs2Version
    val Fs2IO       = "co.fs2"            %% "fs2-io"             % LibraryVersions.fs2Version % "test"
    val Mouse       = Def.setting("org.typelevel" %%% "mouse" % LibraryVersions.mouseVersion)
    val Shapeless   = Def.setting("com.chuusai" %%% "shapeless" % LibraryVersions.shapelessVersion)
    val CommonsHttp = "commons-httpclient" % "commons-httpclient" % LibraryVersions.commonsHttp
    val UnboundId   =
      "com.unboundid" % "unboundid-ldapsdk-minimal-edition" % LibraryVersions.unboundId
    val JwtCore          = "com.pauldijou" %% "jwt-core"     % LibraryVersions.jwt
    val JwtCirce         = "com.pauldijou" %% "jwt-circe"    % LibraryVersions.jwt
    val Slf4j            = "org.slf4j"      % "slf4j-api"    % LibraryVersions.slf4j
    val JuliSlf4j        = "org.slf4j"      % "jul-to-slf4j" % LibraryVersions.slf4j
    val NopSlf4j         = "org.slf4j"      % "slf4j-nop"    % LibraryVersions.slf4j
    val CatsTime         = Def.setting(
      "io.chrisdavenport" %%% "cats-time" % LibraryVersions.catsTime % "compile->compile;test->test"
    )
    val Log4Cats         = Def.setting("org.typelevel" %%% "log4cats-slf4j" % LibraryVersions.log4cats)
    val Log4CatsNoop     =
      Def.setting("org.typelevel" %%% "log4cats-noop" % LibraryVersions.log4cats % "test")
    val Logback          = Seq(
      "ch.qos.logback"       % "logback-core"             % LibraryVersions.logback,
      "ch.qos.logback"       % "logback-classic"          % LibraryVersions.logback,
      "org.codehaus.janino"  % "janino"                   % LibraryVersions.janino,
      "net.logstash.logback" % "logstash-logback-encoder" % LibraryVersions.logstash
    )
    val Log4s            = Def.setting("org.log4s" %%% "log4s" % LibraryVersions.log4s)
    val Log4CatsLogLevel = Def.setting(
      Seq(
        "org.typelevel" %%% "log4cats-core"     % LibraryVersions.log4cats,
        "com.rpiaggio"  %%% "log4cats-loglevel" % LibraryVersions.log4catsLevel
      )
    )
    val PrometheusClient =
      "io.prometheus" % "simpleclient_common" % LibraryVersions.prometheusClient
    val Logging          = Def.setting(Seq(JuliSlf4j, Log4s.value) ++ Logback)
    val PureConfig       = Seq(
      "com.github.pureconfig" %% "pureconfig"             % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-cats"        % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-cats-effect" % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-http4s"      % LibraryVersions.pureConfig
    )
    val OpenCSV          = "net.sf.opencsv" % "opencsv"          % LibraryVersions.opencsv
    val Squants          = Def.setting("org.typelevel" %%% "squants" % LibraryVersions.squants)
    val ScalaXml         =
      Def.setting("org.scala-lang.modules" %%% "scala-xml" % LibraryVersions.scalaXmlVerson)
    val Http4s           = Seq("org.http4s" %% "http4s-dsl" % LibraryVersions.http4sVersion,
                     "org.http4s" %% "http4s-blaze-server" % LibraryVersions.http4sVersion
    )
    val Http4sClient     = Seq(
      "org.http4s" %% "http4s-dsl"               % LibraryVersions.http4sVersion,
      "org.http4s" %% "http4s-async-http-client" % LibraryVersions.http4sVersion
    )
    val Http4sBoopickle  = "org.http4s"    %% "http4s-boopickle" % LibraryVersions.http4sVersion
    val Http4sCore       = "org.http4s"    %% "http4s-core"      % LibraryVersions.http4sVersion
    val Http4sCirce      = "org.http4s"    %% "http4s-circe"     % LibraryVersions.http4sVersion
    val Http4sXml        = "org.http4s"    %% "http4s-scala-xml" % LibraryVersions.http4sVersion
    val Http4sPrometheus =
      "org.http4s" %% "http4s-prometheus-metrics" % LibraryVersions.http4sVersion
    val Monocle = Def.setting(
      Seq(
        "com.github.julien-truffaut" %%% "monocle-core"   % LibraryVersions.monocleVersion,
        "com.github.julien-truffaut" %%% "monocle-macro"  % LibraryVersions.monocleVersion,
        "com.github.julien-truffaut" %%% "monocle-unsafe" % LibraryVersions.monocleVersion,
        "com.github.julien-truffaut" %%% "monocle-law"    % LibraryVersions.monocleVersion
      )
    )
    val Circe   = Def.setting(
      Seq(
        "io.circe" %%% "circe-core"    % LibraryVersions.circeVersion,
        "io.circe" %%% "circe-generic" % LibraryVersions.circeVersion,
        "io.circe" %%% "circe-parser"  % LibraryVersions.circeVersion,
        "io.circe" %%% "circe-testing" % LibraryVersions.circeVersion % "test"
      )
    )

    // Client Side JS libraries
    val ReactScalaJS            = Def.setting(
      Seq(
        "com.github.japgolly.scalajs-react" %%% "core"             % LibraryVersions.scalajsReact,
        "com.github.japgolly.scalajs-react" %%% "extra"            % LibraryVersions.scalajsReact,
        "com.github.japgolly.scalajs-react" %%% "ext-monocle-cats" % LibraryVersions.scalajsReact,
        "com.github.japgolly.scalajs-react" %%% "ext-cats"         % LibraryVersions.scalajsReact
      )
    )
    val Diode                   = Def.setting(
      Seq(
        "io.suzaku" %%% "diode"       % LibraryVersions.diode,
        "io.suzaku" %%% "diode-react" % LibraryVersions.diode
      )
    )
    val ScalaJSDom              = Def.setting("org.scala-js" %%% "scalajs-dom" % LibraryVersions.scalaDom)
    val ScalaJSReactCommon      =
      Def.setting("io.github.cquiroz.react" %%% "common" % LibraryVersions.scalaJSReactCommon)
    val ScalaJSReactCats        =
      Def.setting("io.github.cquiroz.react" %%% "cats" % LibraryVersions.scalaJSReactCommon)
    val ScalaJSReactSemanticUI  = Def.setting(
      "io.github.cquiroz.react" %%% "react-semantic-ui" % LibraryVersions.scalaJSSemanticUI
    )
    val ScalaJSReactVirtualized = Def.setting(
      "io.github.cquiroz.react" %%% "react-virtualized" % LibraryVersions.scalaJSReactVirtualized
    )
    val ScalaJSReactDraggable   = Def.setting(
      "io.github.cquiroz.react" %%% "react-draggable" % LibraryVersions.scalaJSReactDraggable
    )
    val ScalaJSReactSortable    = Def.setting(
      "io.github.cquiroz.react" %%% "react-sortable-hoc" % LibraryVersions.scalaJSReactSortable
    )
    val ScalaJSReactClipboard   = Def.setting(
      "io.github.cquiroz.react" %%% "react-clipboard" % LibraryVersions.scalaJSReactClipboard
    )
    val BooPickle               = Def.setting("io.suzaku" %%% "boopickle" % LibraryVersions.booPickle)
    val JavaTimeJS              =
      Def.setting("io.github.cquiroz" %%% "scala-java-time" % LibraryVersions.javaTimeJS)
    val GeminiLocales           =
      Def.setting("edu.gemini" %%% "gemini-locales" % LibraryVersions.geminiLocales)
    val PPrint                  = Def.setting("com.lihaoyi" %%% "pprint" % LibraryVersions.pprint)

    // OCS Libraries, these should become modules in the future
    val SpModelCore = "edu.gemini.ocs" %% "edu-gemini-spmodel-core" % LibraryVersions.ocsVersion
    val SeqexecOdb  = Seq(
      "edu.gemini.ocs" %% "edu-gemini-seqexec-odb" % LibraryVersions.ocsVersion,
      ("dom4j"          % "dom4j"                  % "1.5.1")
        .exclude("jaxen", "jaxen")
        .exclude("jaxme", "jaxme-api")
        .exclude("msv", "xsdlib")
        .exclude("msv", "relaxngDatatype")
        .exclude("pull-parser", "pull-parser")
        .exclude("stax", "stax")
        .exclude("xml-apis", "xml-apis")
        .exclude("xpp3", "xpp3")
    )
    val POT         = "edu.gemini.ocs" %% "edu-gemini-pot"          % LibraryVersions.ocsVersion
    val TRPC        = "edu.gemini.ocs" %% "edu-gemini-util-trpc"    % LibraryVersions.ocsVersion
    val WDBAClient  = Seq(
      "edu.gemini.ocs"   %% "edu-gemini-wdba-session-client" % LibraryVersions.ocsVersion,
      "org.apache.xmlrpc" % "xmlrpc-client"                  % LibraryVersions.apacheXMLRPC
    )

    val JAXB = Seq("javax.xml.bind" % "jaxb-api" % LibraryVersions.jaxb,
                   "org.glassfish.jaxb" % "jaxb-runtime" % LibraryVersions.jaxb
    )

    // GIAPI Libraries
    val EpicsService       = "edu.gemini.epics" % "epics-service" % LibraryVersions.epicsService
    val GmpCommandsRecords =
      "edu.gemini.gmp" % "gmp-commands-records" % LibraryVersions.gmpCommandRecords
    val GiapiJmsUtil     = "edu.gemini.aspen" % "giapi-jms-util" % LibraryVersions.giapiJmsUtil
    val GiapiJmsProvider =
      "edu.gemini.jms" % "jms-activemq-provider" % LibraryVersions.giapiJmsProvider
    val Giapi               = "edu.gemini.aspen" % "giapi" % LibraryVersions.giapi
    val GiapiCommandsClient =
      "edu.gemini.aspen.gmp" % "gmp-commands-jms-client" % LibraryVersions.giapiCommandsClient
    val GiapiStatusService =
      "edu.gemini.aspen" % "giapi-status-service" % LibraryVersions.giapiStatusService
    val GmpStatusGateway =
      "edu.gemini.aspen.gmp" % "gmp-status-gateway" % LibraryVersions.gmpStatusGateway
    val GmpStatusDatabase =
      "edu.gemini.aspen.gmp" % "gmp-statusdb" % LibraryVersions.gmpStatusDatabase
    val GmpCmdJmsBridge =
      "edu.gemini.aspen.gmp" % "gmp-commands-jms-bridge" % LibraryVersions.gmpCmdClientBridge
    val Guava = "com.google.guava" % "guava" % LibraryVersions.guava

    // Lucuma Libraries
    val LucumaCore = Def.setting(
      Seq(
        "edu.gemini" %%% "lucuma-core"         % LibraryVersions.lucumaCore,
        "edu.gemini" %%% "lucuma-core-testkit" % LibraryVersions.lucumaCore
      )
    )
    val LucumaUI   = Def.setting("edu.gemini" %%% "lucuma-ui" % LibraryVersions.lucumaUI)
  }

  object PluginVersions {
    // Compiler plugins
    val kpVersion        = "0.13.0"
    val betterMonadicFor = "0.3.1"
  }

  object Plugins {
    val kindProjectorPlugin =
      ("org.typelevel" % "kind-projector" % PluginVersions.kpVersion).cross(CrossVersion.full)
    val betterMonadicForPlugin =
      "com.olegpy" %% "better-monadic-for" % PluginVersions.betterMonadicFor
  }

}
