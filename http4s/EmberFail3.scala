//> using scala "2.13.10"

//> using lib "org.http4s::http4s-dsl:0.23.24"
//> using lib "org.http4s::http4s-ember-client:0.23.24"
//> using lib "org.http4s::http4s-ember-server:0.23.24"

// scala-cli run -w http4s/EmberFail3.scala

import cats.effect._
import cats.implicits._
import com.comcast.ip4s._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._
import org.http4s.ember.server._
import org.http4s.implicits._
import org.http4s.headers
import org.http4s.server.Router
import org.http4s.{HttpRoutes, Uri}
import fs2.io.file.{Path => FPath}

import org.http4s.ember.client.EmberClient
import org.http4s.ember.client.EmberClientBuilder
import scala.concurrent.duration._
import fs2.io.file.Files
import org.http4s.Request
import org.http4s.Method
import org.http4s.Headers
import cats.effect.std.Random

// File service that writes to disk slowly
class LocalFileService {
  private val fs2FileIO = fs2.io.file.Files[IO]

  def upload(
      path: FPath,
      length: Long,
      content: fs2.Stream[IO, Byte]
  ): IO[Unit] = {
    val write = content
      .groupWithin(10000, 1.second) // 10 mb per
      .metered(1.second) // 1.second
      .unchunks
      .through(fs2FileIO.writeAll(path))
      .compile
      .drain

    val log = IO.println(s"done $path")

    write *> log
  }
}

/** Have a client send data request to server. Each request is streamed from the
  * client to the server. The server know the length of the incoming request
  * body. The server writes it to disk.
  */
object EmberFail1 extends IOApp.Simple {
  val filesIo = Files[IO]
  def body(size: Long) = fs2.Stream.emits("test".getBytes()).covary[IO]

  def run: IO[Unit] = {
    def uploadRoutes(fs: LocalFileService) = HttpRoutes.of[IO] {
      case req @ POST -> Root / "upload" =>
        req.headers.get[headers.`Content-Length`] match {
          case None =>
            BadRequest()
          case Some(value) =>
            val writeFile = filesIo.tempFile
              .use { path =>
                fs.upload(path, value.length, req.body)
              }
            writeFile *> Ok()
        }
    }

    val postRequests = EmberClientBuilder.default[IO].build.use { client =>
      val clientDsl = Http4sClientDsl[IO]
      import clientDsl._

      def sizedRequest(length: Long) = {
        val allHeaders = Headers(headers.`Content-Length`(length))
        Request[IO](
          Method.POST,
          uri"http://localhost:8080/upload",
          headers = allHeaders,
          body = body(length)
        )
      }

      def sendIt(size: Long) = {
        IO.println(s"sending $size bytes") *>
          client
            .successful(sizedRequest(size))
            .flatMap(res => IO.println(s"success for $size bytes, $res"))

      }

      Random.scalaUtilRandom[IO].flatMap { random =>
        fs2.Stream
          .repeatEval {
            random.betweenLong(
              1024 * 100, // 10kb
              1024 * 5000 // 5mb
            )
          }
          // if you switch to evalMap here, you won't get and issue
          // .parEvalMap(10) { sendIt }
          .evalMap { sendIt }
          .take(10)
          .compile
          .drain
      }
    }

    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"8080")
      .withHttpApp(uploadRoutes(new LocalFileService).orNotFound)
      .build
      .use(_ => postRequests)
  }
}
