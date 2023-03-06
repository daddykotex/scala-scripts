//> using scala "2.13.10"

//> using lib "org.http4s::http4s-dsl:0.23.18"
//> using lib "org.http4s::http4s-ember-client:0.23.18"
//> using lib "org.http4s::http4s-ember-server:0.23.18"

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
  * The particular thing is that the body is a Stream. So the request contains a
  * `Transfer-Encoding: chunked` header.
  */
object EmberFail1 extends IOApp.Simple {
  def body() = fs2.Stream.emits("test".getBytes()).covary[IO]

  def run: IO[Unit] = {
    val resp = Ok().flatMap(r => IO.println(s"SERVER $r").as(r))

    val failRoutes = HttpRoutes.of[IO] {
      case req @ POST -> Root / "test" / "post" =>
        IO.println(s"SERVER req: $req") *> resp
    }
    val successRoutes = HttpRoutes.of[IO] {
      case req @ POST -> Root / "test" / "post" =>
        IO.println(s"SERVER req: $req") *>
          req.body.compile.drain *> resp
    }
    val postRequests = EmberClientBuilder.default[IO].build.use { client =>
      val clientDsl = Http4sClientDsl[IO]
      import clientDsl._

      val req =
        POST(Uri.unsafeFromString("http://localhost:8080/test/post?param=XXX)"))
          .withBodyStream(body())

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
      // use successRoutes to see it work
      .withHttpApp(failRoutes.orNotFound)
      .build
      .use(_ => postRequests)
  }
}
