//> using scala "3.3.0"
//> using lib "org.typelevel::cats-effect:3.5.1"
//> using lib "org.http4s::http4s-core:0.23.23"
//> using lib "org.http4s::http4s-client:0.23.23"
//> using lib "org.http4s::http4s-ember-client:0.23.23"
//> using lib "org.http4s::http4s-ember-server:0.23.23"
//> using lib "org.msgpack:msgpack-core:0.9.4"

// scala-cli run http4s-proxy.scala -- "https://sts.us-east-1.amazonaws.com"

import cats.effect._
import com.comcast.ip4s._
import cats.syntax.all._
import fs2.Stream
import cats.implicits._
import org.http4s._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import org.typelevel.ci._
import org.msgpack.core._
import scala.jdk.CollectionConverters._

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

    for {
      r <- req.toStrict(None)
      _ <- IO.println(req.asCurl(redactHeadersWhen = _ => false))
      _ <- handleMsgPack(req)
      _ <- handleJson(req)
      res <- client.toHttpApp(newReq)
    } yield res
  }

  def handleJson(body: Media[IO]): IO[Unit] = {
    if (body.contentType.exists(_.mediaType.show == "application/json")) {
      for {
        body <- body.bodyText.compile.foldMonoid
        _ <- IO.println(body)
      } yield ()
    } else {
      IO.unit
    }
  }

  def handleMsgPack(body: Media[IO]): IO[Unit] = {
    if (body.contentType.exists(_.mediaType.show == "application/msgpack")) {
      for {
        bytes <- body.body.compile.to(Array)
        unpackerR = Resource.make(
          IO.delay(MessagePack.newDefaultUnpacker(bytes))
        )(x => IO.delay(x.close()))
        _ <- unpackerR.use { unpacker =>
          val format = unpacker.getNextFormat()

          val value = unpacker.unpackValue().asArrayValue()

          IO.println(s"format: $format") *>
            IO.println(s"values: ") *>
            value.list().asScala.toList.traverse(IO.println)
        }
      } yield ()
    } else {
      IO.unit
    }
  }

  def stream(uri: Uri): Stream[IO, Nothing] = {
    for {
      client <- Stream.resource(EmberClientBuilder.default[IO].build)
      finalHttpApp = proxiedTo(client, uri)

      exitCode <- Stream.resource(
        EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8081")
          .withHttpApp(finalHttpApp)
          .build >>
          Resource.eval(IO.never)
      )
    } yield exitCode
  }.drain
}
