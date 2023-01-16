//> using lib "com.github.daddykotex::courier:3.2.0"

import courier._, Defaults._
import scala.util.Success
import scala.util.Failure
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._

def subject(): String = "There you go"
def body(name: String): String = s"""|Hey $name,
                                     |
                                     |This is how you do it.
                                     |""".stripMargin

/** EMAIL_PASS is an app password. To create an app password, head to
  * https://myaccount.google.com/security and
  *
  *   1. activate 2FA
  *   1. create an app password
  */
@main()
def main() =
  val emails = List("David Francoeur" -> "dfrancoeur04@gmail.com")
  val mailer = Mailer("smtp.gmail.com", 587)
    .auth(true)
    .as("dfrancoeur04@gmail.com", sys.env("EMAIL_PASS"))
    .startTls(true)()
  val mails = emails.map { case (name, email) =>
    Envelope
      .from("dfrancoeur04" `@` "gmail.com")
      .to(email.addr)
      .subject(subject())
      .content(Text(body(name)))
  }

  val res = Future.traverse(mails) { e =>
    val fut = mailer(e)
    fut.onComplete {
      case Success(_) => ()
      case Failure(_) => println(s"Delivery to ${e._to} has failed.")
    }
    fut
  }
  Await.result(res, 2.minutes)
