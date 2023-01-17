//> using scala "3.2.1"
//> using lib "org.typelevel::cats-effect:3.4.0"
//> using lib "org.http4s::http4s-core:0.23.17"
//> using lib "org.http4s::http4s-client:0.23.17"
//> using lib "org.http4s::http4s-ember-client:0.23.17"
//> using lib "org.http4s::http4s-ember-server:0.23.17"

// scala-cli run http4s-proxy.scala -- "https://sts.us-east-1.amazonaws.com"

import cats.effect._
import com.comcast.ip4s._
import cats.syntax.all._
import fs2.Stream
import org.http4s._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import org.typelevel.ci.CIString.apply
import org.typelevel.ci.CIString

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    args.headOption
      .toRight(new RuntimeException("Need one arg: http://some-host.com"))
      .liftTo[IO]
      .flatMap { rawUri =>
        Uri.fromString(args.head).liftTo[IO]
      }
      .flatMap(uri =>
        IO.println(s"Proxying to ${uri.renderString}") *>
          QuickstartServer.stream(uri).compile.drain.as(ExitCode.Success)
      )
}

object QuickstartServer {

  def proxiedTo(client: Client[IO], uri: Uri): HttpApp[IO] = HttpApp { req =>
    val newReq = req.withUri(uri.resolve(req.uri))

    IO.println(req.asCurl(redactHeadersWhen = _ => false)) *>
      client.toHttpApp(newReq)
  }

  def stream(uri: Uri): Stream[IO, Nothing] = {
    for {
      client <- Stream.resource(EmberClientBuilder.default[IO].build)
      finalHttpApp = Logger.httpApp[IO](
        true,
        true,
        logAction = Some { IO.println(_) }
      )(proxiedTo(client, uri))

      exitCode <- Stream.resource(
        EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(finalHttpApp)
          .build >>
          Resource.eval(IO.never)
      )
    } yield exitCode
  }.drain
}
