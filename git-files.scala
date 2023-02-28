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
import NestedFilter._

case class NestedFilter(
    build: BuildFileFilter,
    previous: Option[FileFilter]
) {
  def getFilter(p: io.file.Path): IO[FileFilter] = build(p).map {
    case None             => previous.getOrElse(_ => true)
    case Some(fileFilter) => fileFilter
  }
}
object NestedFilter {
  type FileFilter = io.file.Path => Boolean
  type GetFileFilter = IO[Option[FileFilter]]

  type Dir = io.file.Path
  type BuildFileFilter = Dir => GetFileFilter
}

object Main extends IOApp.Simple {
  val fs2Files = io.file.Files[IO]

  override def run: IO[Unit] = {
    def getGitIgnore(dir: io.file.Path): IO[Option[GitIgnore]] = {
      fs2Files
        .readUtf8(dir./(".gitignore"))
        .compile
        .last
        .map(_.map(GitIgnore.parse))
        .handleError { case _: NoSuchFileException => None }
    }

    // Given a directory, check for the gitignore file
    // if there, return Some(filter*)
    // else return None
    // filter is a predicate that return true if the file is _not_ gitignore, otherwise false
    fs2Files.currentWorkingDirectory.flatMap { workDir =>
      val nestedFilter: NestedFilter = NestedFilter(
        { dir =>
          getGitIgnore(dir).map { maybeGitIgnore =>
            maybeGitIgnore.map(gitIgnore => { file =>
              gitIgnore.isAllowed(workDir.relativize(file).toString)
            })
          }
        },
        None
      )

      walkGit(workDir, nestedFilter)
        .evalTap(IO.println)
        .compile
        .drain
    }
  }

  def walkGit(
      start: io.file.Path,
      nestedFilter: NestedFilter,
      maxDepth: Int = Int.MaxValue
  ): fs2.Stream[IO, io.file.Path] = {
    def go(
        start: io.file.Path,
        nestedFilter: NestedFilter,
        maxDepth: Int
    ): fs2.Stream[IO, io.file.Path] = {
      Stream.emit(start) ++ {
        if (maxDepth == 0) Stream.empty
        Stream
          .eval(fs2Files.getBasicFileAttributes(start, followLinks = false))
          .flatMap { attr =>
            if (attr.isDirectory) {
              Stream.eval(nestedFilter.getFilter(start)).flatMap { filter =>
                val nextLevelFilter = nestedFilter.copy(previous = Some(filter))
                fs2Files
                  .list(start)
                  .filter(filter)
                  .flatMap { path =>
                    go(
                      path,
                      nextLevelFilter,
                      maxDepth - 1
                    )
                  }
                  .mask
              }
            } else Stream.empty
          }
      }
    }

    Stream.eval(
      fs2Files.getBasicFileAttributes(start, followLinks = false)
    ) >> {
      go(start, nestedFilter, maxDepth)
    }

  }

}
