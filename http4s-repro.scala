//> using scala "2.13.10"
//> using repository "https://s01.oss.sonatype.org/content/repositories/snapshots/"
//> using lib "org.http4s::http4s-dsl:0.23.19"
//> using lib "org.http4s::http4s-ember-client:0.23.19"
//> using lib "org.http4s::http4s-ember-server:0.23.19"

// remove the following dep to reproduce the issue
//> using lib "co.fs2::fs2-io:3.7.0-52-0a912a2-SNAPSHOT"

import cats.effect._
import cats.implicits._
import com.comcast.ip4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server._
import org.http4s.implicits._

import cats.effect._
import org.http4s._
import org.http4s.dsl.Http4sDsl

import scala.concurrent.duration._

object Routes extends Http4sDsl[IO] {
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      IO.println(System.currentTimeMillis()) *> Ok()
  }
}

object Main extends IOApp.Simple {

  val makeServer = EmberServerBuilder
    .default[IO]
    .withPort(port"8080")
    .withHost(host"0.0.0.0")
    .withHttpApp(Routes.routes.orNotFound)
    .build
    .evalTap(_ => IO.println("STARTED"))

  val makeClient = EmberClientBuilder
    .default[IO]
    .build

  val backgroundServer = makeServer.useForever.background

  val run = (backgroundServer *> makeClient).use { client =>
    val uri = uri"http://localhost:8080"
    val spam = fs2.Stream.range(0, 10000).covary[IO].parEvalMap(50) { _ =>
      client.expect[Unit](uri / "health")
    }

    spam.compile.drain *> IO.sleep(45.seconds)
  }
}
