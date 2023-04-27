//> using scala "2.13.10"
//> using lib "org.typelevel::cats-core:2.9.0"
//> using lib "org.typelevel::cats-effect:3.4.9"

import cats._
import cats.implicits._
import cats.effect.implicits._
import cats.effect.IO

case class Kernel(values: Map[String, Int])
object Kernel {
  implicit val m: Monoid[Kernel] = ???
}

class SomeTypeWithEffect {
  def doSomething[F[_]: Applicative](): F[Kernel] = {
    val getSomeKernel: F[Kernel] = ???
    val getOtherKernel: F[Kernel] = ???

    List(getOtherKernel, getSomeKernel).foldA
  }
  def doSomething2[F[_]: Functor: Semigroupal](): F[Kernel] = {
    val getSomeKernel: F[Kernel] = ???
    val getOtherKernel: F[Kernel] = ???

    (getOtherKernel, getSomeKernel).mapN(_ combine _)
  }

  def doSomething2[F[_]: NonEmptyParallel](): F[Kernel] = {
    val getSomeKernel: F[Kernel] = ???
    val getOtherKernel: F[Kernel] = ???

    (getOtherKernel, getSomeKernel).parMapN(_ combine _)
  }
}

class SomeTypeWithIO {
  def doSomething() = {
    val getSomeKernel: IO[Kernel] = ???
    val getOtherKernel: IO[Kernel] = ???

    // work
    (getOtherKernel, getOtherKernel).combineAll
  }
}
