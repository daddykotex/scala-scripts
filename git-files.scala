//> using scala "2.13.10"
//> using lib "co.fs2::fs2-core:3.5.0"
//> using lib "co.fs2::fs2-io:3.5.0"
//> using lib "com.github.arturopala::gitignore:0.4.0"

import cats.effect.IOApp
import cats.effect.IO
import com.github.arturopala.gitignore.GitIgnore
import cats.implicits._
import fs2._
import java.nio.file.NoSuchFileException

object Main extends IOApp.Simple {
  val fs2Files = io.file.Files[IO]

  override def run: IO[Unit] = {
    val workDir =
      io.file.Path("/Users/David.Francoeur/workspace/dev/scala-scripts")
    walkGit(workDir)
      .evalTap(p => IO.println(workDir.relativize(p)))
      .compile
      .drain
  }

  def walkGit(
      start: io.file.Path,
      maxDepth: Int = Int.MaxValue
  ): fs2.Stream[IO, io.file.Path] = {
    def go(
        _start: io.file.Path,
        maxDepth: Int,
        gitIgnore: Option[GitIgnore]
    ): fs2.Stream[IO, io.file.Path] = {
      Stream.emit(_start) ++ {
        if (maxDepth == 0) Stream.empty
        Stream
          .eval(fs2Files.getBasicFileAttributes(_start, followLinks = false))
          .flatMap { attr =>
            if (attr.isDirectory)
              fs2.Stream.eval(getGitIgnore(_start)).flatMap {
                currentGitIgnore =>
                  val finalGitIgnore = currentGitIgnore.orElse(gitIgnore)
                  def allowed(p: io.file.Path): Boolean = {
                    val relativePath = start.relativize(p).toString
                    finalGitIgnore.exists(_.isAllowed(relativePath))
                  }

                  fs2Files
                    .list(_start)
                    .filter(allowed)
                    .flatMap { path =>
                      go(
                        path,
                        maxDepth - 1,
                        finalGitIgnore
                      )
                    }
                    .mask
              }
            else
              Stream.empty
          }
      }
    }

    def getGitIgnore(dir: io.file.Path): IO[Option[GitIgnore]] = {
      fs2Files
        .readUtf8(dir./(".gitignore"))
        .compile
        .last
        .map(_.map(GitIgnore.parse))
        .handleError { case _: NoSuchFileException => None }
    }

    Stream.eval(
      fs2Files.getBasicFileAttributes(start, followLinks = false)
    ) >> {
      go(start, maxDepth, None)
    }

  }

}
