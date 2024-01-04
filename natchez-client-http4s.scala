//> using scala "3.3.0"
//> using lib "org.http4s::http4s-ember-client:0.23.23"
//> using lib "org.tpolecat::natchez-http4s:0.5.0"
//> using lib "org.tpolecat::natchez-datadog:0.3.5"

import cats.effect._
import cats.implicits._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Stream
import natchez.Trace
import natchez.datadog.DDTracer
import natchez.http4s.NatchezMiddleware
import org.http4s._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits._
import org.http4s.implicits._
import org.typelevel.ci._

import java.net.URI

object Main extends IOApp.Simple {

  override def run: IO[Unit] = {
    val p = for {
      ep <- makeEp
      t <- Trace.ioTraceForEntryPoint(ep).toResource
      client <- makeClient
      _ <- execute(client)(t).toResource
    } yield ()

    p.use_.as(ExitCode.Success)
  }

  def execute(client: Client[IO])(implicit trace: Trace[IO]): IO[Unit] = {
    val tracedClient = NatchezMiddleware.client[IO](client)
    fs2.Stream
      .range(0, 10)
      .map(i => s"message-$i")
      .covary[IO]
      .evalMap { message =>
        trace.span("processing message") {
          trace.put("message_id" -> message) *>
            makeRequests(tracedClient)
        }
      }
      .compile
      .drain
  }

  def makeRequests(client: Client[IO]): IO[Unit] = {
    val r1 = client
      .statusFromUri(
        uri"https://api-registry-overlay.us-east-1.dpegrid.net/overlay/health_check"
      )
      .void
    val r2 = client
      .statusFromUri(
        uri"https://api-registry-overlay.us-east-1.dpegrid.net/overlay/team/APIRegistryTeam"
      )
      .void
    r1 *> r2
  }

  val makeEp = DDTracer
    .entryPoint[IO](
      builder =>
        IO {
          builder
            .serviceName("dfrancoeur-my-app")
            .build()
        },
      Some(new URI("http://127.0.0.1:8126"))
    )
  val makeClient: Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].build
}
