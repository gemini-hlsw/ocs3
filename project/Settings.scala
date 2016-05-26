import sbt._

/**
 * Application settings and dependencies
 */
object Settings {
  object Definitions {
    /** The name of the application */
    val name = "ocs3"

    /** Options for the scala compiler */
    val scalacOptions = Seq(
      "-unchecked",
      "-deprecation",
      "-encoding", "UTF-8", // Keep on the same line
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-target:jvm-1.8",
      "-Xlint",
      "-Xlint:-stars-align"
    )
  }

  /** Library versions */
  object LibraryVersions {
    val scala        = "2.11.8"

    // ScalaJS libraries
    val scalaDom     = "0.9.0"
    val scalajsReact = "0.11.1"
    val scalaCSS     = "0.4.1"
    val uPickle      = "0.4.0"
    val diode        = "0.5.1"
    val javaTimeJS   = "0.1.0"
    val javaLogJS    = "0.1.0"

    // Java libraries
    val scalaZ       = "7.2.2"
    val scalaZStream = "0.8a"
    val streamZ      = "0.3.2"

    val http4s       = "0.13.2a"
    val play         = "2.5.3"
    val squants      = "0.6.2"
    val argonaut     = "6.2-M1"
    val commonsHttp  = "2.0"
    val unboundId    = "3.1.1"
    val jwt          = "0.7.1"

    // test libraries
    val scalaTest    = "3.0.0-RC1"
    val scalaCheck   = "1.13.1"

    // Pure JS libraries
    val reactJS        = "15.0.1"
    val jQuery         = "2.2.1"
    val semanticUI     = "2.1.8"
    val jQueryTerminal = "0.9.3"
    val ocsVersion     = "2016001.1.1"
  }

  /**
    * Global libraries
    */
  object Libraries {
    import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

    // Test Libraries
    val TestLibs = Def.setting(Seq(
      "org.scalatest"  %%% "scalatest"   % LibraryVersions.scalaTest  % "test",
      "org.scalacheck" %%% "scalacheck"  % LibraryVersions.scalaCheck % "test"
    ))

    val Argonaut    = "io.argonaut"        %% "argonaut"                          % LibraryVersions.argonaut
    val CommonsHttp = "commons-httpclient" %  "commons-httpclient"                % LibraryVersions.commonsHttp
    val UnboundId   = "com.unboundid"      %  "unboundid-ldapsdk-minimal-edition" % LibraryVersions.unboundId
    val JwtCore     = "com.pauldijou"      %% "jwt-core"                          % LibraryVersions.jwt

    val Squants     = Def.setting("com.squants"  %%% "squants"              % LibraryVersions.squants)
    val UPickle     = Def.setting("com.lihaoyi"  %%% "upickle"              % LibraryVersions.uPickle)
    val JavaTimeJS  = Def.setting("org.scala-js" %%% "scalajs-java-time"    % LibraryVersions.javaTimeJS)
    val JavaLogJS   = Def.setting("org.scala-js" %%% "scalajs-java-logging" % LibraryVersions.javaLogJS)

    // ScalaZ
    val ScalaZCore       = Def.setting("org.scalaz" %%% "scalaz-core"         % LibraryVersions.scalaZ)
    val ScalaZConcurrent = "org.scalaz"             %%  "scalaz-concurrent"   % LibraryVersions.scalaZ
    val ScalaZStream     = "org.scalaz.stream"      %%  "scalaz-stream"       % LibraryVersions.scalaZStream
    val StreamZ          = "com.github.krasserm"    %%  "streamz-akka-stream" % LibraryVersions.streamZ

    // Server side libraries
    val Http4s  = Seq(
      "org.http4s" %% "http4s-dsl"          % LibraryVersions.http4s,
      "org.http4s" %% "http4s-blaze-server" % LibraryVersions.http4s)

    val Play = Seq(
      "com.typesafe.play" %% "play"              % LibraryVersions.play,
      "com.typesafe.play" %% "play-netty-server" % LibraryVersions.play)

    // Client Side JS libraries
    val ReactScalaJS = Def.setting(Seq(
      "com.github.japgolly.scalajs-react" %%% "core"         % LibraryVersions.scalajsReact,
      "com.github.japgolly.scalajs-react" %%% "extra"        % LibraryVersions.scalajsReact,
      "com.github.japgolly.scalajs-react" %%% "ext-scalaz72" % LibraryVersions.scalajsReact,
      "com.github.japgolly.scalacss"      %%% "ext-react"    % LibraryVersions.scalaCSS
    ))
    val Diode = Def.setting(Seq(
      "me.chrons" %%% "diode"       % LibraryVersions.diode,
      "me.chrons" %%% "diode-react" % LibraryVersions.diode
    ))
    val ScalaCSS   = Def.setting("com.github.japgolly.scalacss" %%% "core"          % LibraryVersions.scalaCSS)
    val ScalaJSDom = Def.setting("org.scala-js"                 %%% "scalajs-dom"   % LibraryVersions.scalaDom)
    val JQuery     = Def.setting("org.querki"                   %%% "jquery-facade" % LibraryVersions.scalaJQuery)

    // OCS Libraries, these should become modules in the future
    val SpModelCore = "edu.gemini.ocs"     %% "edu-gemini-spmodel-core" % LibraryVersions.ocsVersion
    val SeqexecOdb  = "edu.gemini.ocs"     %% "edu-gemini-seqexec-odb"  % LibraryVersions.ocsVersion
    val POT         = "edu.gemini.ocs"     %% "edu-gemini-pot"          % LibraryVersions.ocsVersion
    val EpicsACM    = "edu.gemini.ocs"     %% "edu-gemini-epics-acm"    % LibraryVersions.ocsVersion
    val TRPC        = "edu.gemini.ocs"     %% "edu-gemini-util-trpc"    % LibraryVersions.ocsVersion
  }

}
