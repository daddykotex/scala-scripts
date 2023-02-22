//> using scala "2.13.10"
//> using lib "co.fs2::fs2-core:3.5.0"
//> using lib "co.fs2::fs2-io:3.5.0"

import cats.data.Chain
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Async
import cats.effect.kernel.Fiber
import cats.effect.kernel.Resource
import cats.effect.Ref
import cats.effect.std.QueueSink
import cats.effect.syntax.spawn._
import cats.effect.syntax.temporal._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.concurrent.Channel
import scala.concurrent.duration._

object Main extends IOApp.Simple {

  val prog = Ref.of[IO, Chain[String]](Chain.empty).flatMap { ref =>
    val sink: fs2.Pipe[IO, String, Unit] = {
      _.groupWithin(250, 100.millis)
        .evalMap { xs => xs.traverse(x => ref.update(_.append(x))).void }
    }
    StreamedFunnel(capacity = 50, 10.seconds, sink)
      .use { funnel =>
        val p1 = funnel.offer("p1")
        val p2 = funnel.offer("p2") *> IO.sleep(100.millis)
        p1 *> p2
      } *> ref.get
  }

  override def run: IO[Unit] = prog.flatMap(result => IO.println(result))
}

object StreamedFunnel {
  def apply[F[_]: Async, T](
      capacity: Int,
      drainTimeout: FiniteDuration,
      process: fs2.Pipe[F, T, Unit]
  ): Resource[F, QueueSink[F, T]] = {
    for {
      channel <- Resource.eval(Channel.bounded[F, T](capacity))
      _ <- consume(channel, drainTimeout)(process)
    } yield new QueueSink[F, T] {

      /** channel.send returns a Either[Channel.Closed, Unit]. When you get a
        * Channel.Closed, it meant that the `send` call was a no-op.
        *
        * channel.send can semantically block, which applies back-pressure
        */
      override def offer(a: T): F[Unit] = channel.send(a).void

      /** channel.trySend returns a Either[Channel.Closed, Unit]. When you get a
        * Channel.Closed, it meant that the `send` call was a no-op. we convert
        * it to `false` to signal the fact that `a` was not sent in the channel
        *
        * this method does not block
        */
      override def tryOffer(a: T): F[Boolean] =
        channel.trySend(a).map(_.getOrElse(true))
    }
  }

  private def consume[F[_]: Async, T](
      channel: Channel[F, T],
      drainTimeout: FiniteDuration
  )(sink: fs2.Pipe[F, T, Unit]): Resource[F, Unit] = {
    def gracefulClosure(f: Fiber[F, Throwable, Unit]) =
      channel.close >> f.join.void.timeoutTo(drainTimeout, f.cancel)
    val consumer = channel.stream
      .through(sink)
      .compile
      .drain
    Resource.make(consumer.start)(gracefulClosure).void
  }
}
