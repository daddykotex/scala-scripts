//> using scala "2.13.10"
//> using lib "co.fs2::fs2-core:3.5.0"
//> using lib "co.fs2::fs2-io:3.5.0"

import cats.effect.IOApp
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.effect.IO
import cats.effect.kernel.Async
import scala.concurrent.duration._

/** Simple program that allocates a Queue then starts a fiber to dequeue from
  * it. The lifetime of the dequeuing fiber is bound to the parent resource and
  * as such, the program terminates before the content of the queue is pulled.
  * If we add a sleep to ensure the program keeps running for a small amount of
  * time, the element is eventually pulled.
  *
  * Is there are way to ensure the that the queue is drained or a timeout
  * occurs.
  */
object Main extends IOApp.Simple {

  val makeQueue = Resource.eval(Queue.bounded[IO, String](10))
  val prog = for {
    _ <- Resource.eval(IO.println("start"))
    q <- makeQueue
    _ <- Resource.eval(q.offer("first"))
    _ <- Async[IO].background(
      fs2.Stream
        .fromQueueUnterminated(q)
        .evalTap(IO.println)
        .compile
        .drain
    )
    _ <- Resource.eval(
      IO.sleep(2.seconds)
    ) // let it drain // if you comment this line, `first` is not printed
    _ <- Resource.eval(IO.println("finish"))
  } yield ()

  override def run: IO[Unit] = prog.use_
}
