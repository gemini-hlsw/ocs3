// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.server.config

import cats.effect.{ Blocker, ContextShift, IO }
import lucuma.core.enum.Site
import java.nio.file.Paths
import org.http4s.Uri
import org.http4s.Uri._
import pureconfig._
import scala.concurrent.duration._
import seqexec.model.config._
import shapeless.tag
import cats.tests.CatsSuite
import scala.concurrent.ExecutionContext

class ConfigurationLoaderSpec extends CatsSuite {
  val gcal   =
    SmartGcalConfiguration(uri("gsodbtest.gemini.edu"), Paths.get("/tmp/smartgcal"))
  val tls    = TLSConfig(Paths.get("file.jks"), "key", "cert")
  val auth   = AuthenticationConfig(2.hour,
                                  "SeqexecToken",
                                  "somekey",
                                  false,
                                  List(uri("ldap://sbfdc-wv1.gemini.edu:3268"))
  )
  val ws     = WebServerConfiguration("0.0.0.0", 7070, 7071, "localhost", Some(tls))
  val server = SeqexecEngineConfiguration(
    uri("localhost"),
    uri("http://cpodhsxx:9090/axis2/services/dhs/images"),
    SystemsControlConfiguration(
      altair = ControlStrategy.Simulated,
      gems = ControlStrategy.Simulated,
      dhs = ControlStrategy.Simulated,
      f2 = ControlStrategy.Simulated,
      gcal = ControlStrategy.Simulated,
      gmos = ControlStrategy.Simulated,
      gnirs = ControlStrategy.Simulated,
      gpi = ControlStrategy.Simulated,
      gpiGds = ControlStrategy.Simulated,
      ghost = ControlStrategy.Simulated,
      ghostGds = ControlStrategy.Simulated,
      gsaoi = ControlStrategy.Simulated,
      gws = ControlStrategy.Simulated,
      nifs = ControlStrategy.Simulated,
      niri = ControlStrategy.Simulated,
      tcs = ControlStrategy.Simulated
    ),
    true,
    false,
    2,
    3.seconds,
    tag[GpiSettings][Uri](uri("vm://gpi?marshal=false&broker.persistent=false")),
    tag[GpiSettings][Uri](uri("http://localhost:8888")),
    tag[GhostSettings][Uri](uri("vm://ghost?marshal=false&broker.persistent=false")),
    tag[GhostSettings][Uri](uri("http://localhost:8888")),
    "tcs=tcs:, ao=ao:, gm=gm:, gc=gc:, gw=ws:, m2=m2:, oiwfs=oiwfs:, ag=ag:, f2=f2:, gsaoi=gsaoi:, aom=aom:, myst=myst:, rtc=rtc:",
    Some("127.0.0.1"),
    0,
    5.seconds,
    10.seconds
  )
  val ref    = SeqexecConfiguration(Site.GS, Mode.Development, server, ws, gcal, auth)

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  val blocker                                 = Blocker.liftExecutionContext(ExecutionContext.global)
  test("read config") {
    assert(
      loadConfiguration[IO](ConfigSource.string(conf), blocker).unsafeRunSync() === ref
    )
  }

  val conf = """
#
# Seqexec server configuration for development mode
#

# mode can be dev in which case fake authentication is supported and the UI provides some extra tools
mode = dev
site = GS

# Authentication related settings
authentication {
    # Indicates how long a session is valid in hrs
    sessionLifeHrs = 2 hours
    # Name of the cookie to store the session
    cookieName = "SeqexecToken"
    # Secret key for JWT tokens
    secretKey = "somekey"
    # List of LDAP servers, the list is used in a failover fashion
    ldapURLs = ["ldap://sbfdc-wv1.gemini.edu:3268"]
}

# Web server related configuration
web-server {
    # Interface to listen on, 0.0.0.0 listens in all interfaces, production instances should be more restrictive
    host = "0.0.0.0"
    # Port to serve https requests
    port = 7070
    # Port for redirects to https
    insecurePort = 7071
    # External url used for redirects
    externalBaseUrl = "localhost"
    tls {
        keyStore = "file.jks"
        keyStorePwd = "key"
        certPwd = "cert"
    }
}

smart-gcal {
    # We normally always use GS for smartGCalDir
    smartGCalHost = "gsodbtest.gemini.edu"
    # Tmp file for development
    smartGCalDir = "/tmp/smartgcal"
}

# Configuration of the seqexec engine
seqexec-engine {
    # host for the odb
    odb = localhost
    dhsServer = "http://cpodhsxx:9090/axis2/services/dhs/images"
    # Tells Seqexec how to interact with a system:
    #   full: connect and command the system
    #   readOnly: connect, but only to read values
    #   simulated: don't connect, simulate internally
    systemControl {
        dhs = simulated
        f2 = simulated
        gcal = simulated
        ghost = simulated
        ghostGds = simulated
        gmos = simulated
        gnirs = simulated
        gpi = simulated
        gpiGds = simulated
        gsaoi = simulated
        gws = simulated
        nifs = simulated
        niri = simulated
        tcs = simulated
        altair = simulated
        gems = simulated
    }
    odbNotifications = true
    # Set to true on development to simulate errors on f2
    instForceError = false
    # if instForceError is true fail at the given iteration
    failAt = 2
    odbQueuePollingInterval = 3 seconds
    tops = "tcs=tcs:, ao=ao:, gm=gm:, gc=gc:, gw=ws:, m2=m2:, oiwfs=oiwfs:, ag=ag:, f2=f2:, gsaoi=gsaoi:, aom=aom:, myst=myst:, rtc=rtc:"
    epicsCaAddrList = 127.0.0.1
    readRetries = 0
    ioTimeout = 5 seconds
    dhsTimeout = 10 seconds
    gpiUrl = "vm://gpi?marshal=false&broker.persistent=false"
    gpiGDS = "http://localhost:8888"
    ghostUrl = "vm://ghost?marshal=false&broker.persistent=false"
    ghostGDS = "http://localhost:8888"
}

"""
}
