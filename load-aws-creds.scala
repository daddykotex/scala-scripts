//> using scala "3.2.1"
//> using lib "org.typelevel::cats-effect:3.4.0"

import cats.effect.kernel.Resource
import cats.effect.IO
import cats.syntax.all._
import java.nio.file.Paths
import cats.effect.IOApp

final case class Credentials(
    key: String,
    secret: String,
    sessionToken: Option[String]
)
object Credentials:
  def fromMap(data: Map[String, String]): Credentials = Credentials(
    data("aws_access_key_id"),
    data("aws_secret_access_key"),
    data.get("aws_session_token")
  )

final case class AwsCredentialsFile(
    default: Option[Credentials],
    profiles: Map[String, Credentials]
)

object AwsCredentialsFile:
  def fromMap(
      dataPerProfile: Map[String, Map[String, String]]
  ): AwsCredentialsFile =
    val default = dataPerProfile.get("default").map(Credentials.fromMap)
    val others = dataPerProfile
      .filterNot(_._1 == "default")
      .map { case (profile, data) =>
        profile -> Credentials.fromMap(data)
      }
    AwsCredentialsFile(default, others)

object Main extends IOApp.Simple:
  def run: IO[Unit] = loadFile(System.getProperty("user.home"))
    .use(lines => processFileLines(lines).pure[IO])
    .flatMap(creds => IO.println(creds.toString()))

def processFileLines(lines: List[String]): AwsCredentialsFile =
  def inProfile(
      rest: List[String],
      currentProfile: String,
      data: Map[String, String]
  ): (Map[String, String], List[String]) =
    rest match
      case Nil =>
        (data, Nil)
      case head :: _ if head.trim().startsWith("[") =>
        (data, rest)
      case head :: next =>
        val parts = head.split("=")
        val updated = data + (parts(0).trim() -> parts(1).trim())
        inProfile(next, currentProfile, updated)

  def lookingForProfile(
      rest: List[String],
      data: Map[String, Map[String, String]]
  ): Map[String, Map[String, String]] =
    rest match
      case head :: next =>
        val profile = head.trim() match
          case Profile.Prefixed(profile)    => profile
          case Profile.Default(profile)     => profile
          case Profile.NonPrefixed(profile) => profile

        val (profileData, rest) = inProfile(next, profile, Map.empty)
        lookingForProfile(rest, data + (profile -> profileData))
      case Nil =>
        data

  val data = lookingForProfile(lines, Map.empty)
  AwsCredentialsFile.fromMap(data)

def loadFile(home: String): Resource[IO, List[String]] =
  Resource
    .fromAutoCloseable(
      IO.delay(
        scala.io.Source.fromFile(Paths.get(home, ".aws", "credentials").toUri())
      )
    )
    .map(_.getLines().toList)
    .map(_.filter(_.nonEmpty))

object Profile {
  object Default {
    def unapply(s: String): Option[String] =
      if (s == "[default]") Some("default") else None
  }

  object Prefixed {
    private val reg = "^\\[profile (\\w*)\\]$".r
    def unapply(s: String): Option[String] = s match {
      case reg(first) => Some(first)
      case _          => None
    }
  }

  object NonPrefixed {
    private val reg = "^\\[([\\w_]*)\\]$".r
    def unapply(s: String): Option[String] = s match {
      case reg(first) => Some(first)
      case _          => None
    }
  }
}
