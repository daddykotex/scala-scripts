//> using scala "3.3.0"

//> using lib "org.typelevel::cats-effect:3.5.1"
//> using lib "co.fs2::fs2-core:3.7.0"

import cats.effect._
import scala.concurrent.duration._
import java.util.concurrent.TimeoutException

// scala-cli run -w run-for.scala

object Main extends IOApp.Simple {

  def runFor[A](duration: FiniteDuration)(f: IO[A]): IO[List[A]] = {
    Ref
      .empty[IO, List[A]]
      .flatMap { ref =>
        val f2 = f.flatMap { a => ref.update(_ :+ a) }
        f2.andWait(5.millis)
          .foreverM
          .background
          .surround {
            IO.sleep(duration) *> ref.get
          }
      }
  }

  def runFor2[T](duration: FiniteDuration)(f: IO[T]): IO[List[T]] =
    Ref.of[IO, List[T]](List.empty[T]).flatMap { ref =>
      val runAndRecord = f.flatMap(t => ref.update(_ :+ t))
      runAndRecord
        .delayBy(5.millis)
        .foreverM
        .timeout(duration)
        .handleErrorWith { case _: TimeoutException => ref.get }
    }

  def runFor3[T](duration: FiniteDuration)(f: IO[T]): IO[List[T]] =
    fs2.Stream
      .repeatEval(f)
      .metered(5.millis)
      .interruptAfter(duration)
      .compile
      .to(List)

  val run =
    runFor(5.seconds)(IO.pure("hey2")).flatMap(x => IO.println(x.size)).void *>
      runFor2(5.seconds)(IO.pure("hey2"))
        .flatMap(x => IO.println(x.size))
        .void *>
      runFor3(5.seconds)(IO.pure("hey2")).flatMap(x => IO.println(x.size)).void
}
