//> using scala "2.13.10"

//> using lib "org.http4s::http4s-dsl:0.23.19-RC1"
//> using lib "org.http4s::http4s-ember-client:0.23.19-RC1"
//> using lib "org.http4s::http4s-ember-server:0.23.19-RC1"

// scala-cli run -w http4s/EmberFail2.scala

import cats.effect._
import cats.implicits._
import com.comcast.ip4s._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._
import org.http4s.ember.server._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.{HttpRoutes, Uri}

import org.http4s.ember.client.EmberClient
import org.http4s.ember.client.EmberClientBuilder
import scala.concurrent.duration._

/** In this repro, I use Ember's client & server. The client sends 100 request
  * to the server. If the server drain the body, then there are no errors.
  *
  * If it does not, it fails.
  *
  * The particular thing is that the response body is a Stream. But the weird
  * thing is that the response is encoded as `Response(status=200,
  * httpVersion=HTTP/1.1, headers=Headers(Content-Length: 0))`
  */
object EmberFail2 extends IOApp.Simple {
  def body() =
    fs2.Stream.emits("test".getBytes()).metered[IO](100.millis)

  def run: IO[Unit] = {
    val resp =
      Ok()
        .map(_.withBodyStream(body()))
        .flatMap(r => IO.println(s"SERVER $r").as(r))

    val routes = HttpRoutes.of[IO] {
      case req @ POST -> Root / "test" / "post" =>
        IO.println(s"SERVER req: $req") *>
          req.body.compile.drain *> resp
    }
    val postRequests = EmberClientBuilder.default[IO].build.use { client =>
      val clientDsl = Http4sClientDsl[IO]
      import clientDsl._

      val req =
        POST(
          "some body",
          Uri.unsafeFromString("http://localhost:8080/test/post?param=XXX)")
        )

      val reqResource = client.run(req)
      List.from(0 until 100).traverse_ { i =>
        IO.println(s"CLIENT req: $req") *>
          reqResource.use { response =>
            IO.println(s"CLIENT resp: $response") *> IO.println("=====")
          }
      }
    }

    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"8080")
      .withHttpApp(routes.orNotFound)
      .build
      .use(_ => postRequests)
  }
}
