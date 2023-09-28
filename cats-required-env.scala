//> using lib "org.typelevel::cats-core:2.10.0"
//> using lib "org.typelevel::cats-effect:3.5.2"

import cats.syntax.functor._
import cats.syntax.traverse._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.MonadThrow
import cats.effect.std.Env
import cats.data.NonEmptyList
import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.IO

object Main extends IOApp.Simple {

  // List(
  //   Left(java.lang.RuntimeException: None of the following environment variables were available: ['oops','oops2']),
  //   Right(/Users/David.Francoeur),
  //   Right(/Users/David.Francoeur)
  // )
  override def run: IO[Unit] = {
    val x = new Example[IO]
    val t1 = x.oneOf("oops", "oops2").attempt
    val t2 = x.oneOf("oops", "HOME").attempt
    val t3 = x.oneOf("HOME", "PWD").attempt
    List(t1, t2, t3).sequence.flatMap(IO.println)
  }

}

class Example[F[_]: Env: MonadThrow] {

  /** Attempt at extracting one of the keys from the environment. Extraction is
    * attempted in the order the keys are provided. As soon as a key is found,
    * the result is returned, if none are found, an exception is raised.
    *
    * @param key
    * @param others
    * @return
    */
  def oneOf(key: String, others: String*): F[String] = {
    def getEnv(k: String): F[Either[String, Unit]] = Env[F]
      .get(k)
      .map {
        _.toLeft(())
      }

    val keys = NonEmptyList.of(key, others: _*)
    keys
      .traverse(getEnv)
      .map(_.sequence)
      .flatMap(
        _.fold[F[String]](
          MonadThrow[F].pure,
          _ =>
            MonadThrow[F].raiseError[String] {
              val kList = keys.map(k => s"'$k'").mkString_("[", ",", "]")
              new RuntimeException(
                s"None of the following environment variables were available: $kList"
              )
            }
        )
      )
  }
}
