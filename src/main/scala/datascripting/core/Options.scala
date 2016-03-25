package datascripting.core

import scalaz.concurrent.Task
import com.couchbase.client.java._
import scopt.{OptionParser, Zero}
import scalaz.stream._
import scalaz._
import argonaut._, Argonaut._
import scala.xml._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.Future.{successful, failed}
import dispatch._
import com.ning.http.client._
import org.joda.time.DateTime
import org.joda.time.format._
import scala.language.postfixOps



object Options {

  def printIssuesAndExit[A](t: ParserResult[A]) = {
    val (es, ss, rv) = t
    println(s"Error Parsing Arguments")
    println("")
    println("Errors:")
    es.foreach(e => println(s"\t$e"))
    println("")
    println("Options Set:")
    ss.foreach(s => println(s"\t$s"))
    System.exit(1)
  }

  object Parser {
    def mysqlConfigParser: Parser[Mysql.MysqlConfig] = {
      Applicative[Parser].apply4(
        stringRequired("mysqlHost", "Required mysqlHost"),
        stringRequired("mysqlUser", "Required mysqlUser"),
        stringOption("mysqlPassword", "Required Mysql Password"),
        stringRequired("mysqlSchema", "Required Mysql Schema")
      ) {
        case (mh, mu, mp, ms) =>
          Mysql.MysqlConfig(
            host     = mh,
            user     = mu,
            password = mp.getOrElse(""),
            schema   = ms
          )
      }
    }

    def couchConfigParser: Parser[Couch.CouchConfig] = {
      Applicative[Parser].apply3(
        fromStringList[String]("couchHosts", identity, "Required CouchHosts"),
        stringRequired("bucket", "Required Bucket Name"),
        stringRequired("bucketPassword", "Required Bucket Password")
      ) {
        case (ch, b, bp) =>
          Couch.CouchConfig(hosts = ch, bucket = b, bucketPassword = bp)
      }
    }

    def dateRequired(name: String, description: String): Parser[DateTime] =
      fromStringRequired(name, Formats.DashedFormat.parseDateTime(_), description)

    def dateOptional(name: String, description: String): Parser[Option[DateTime]] =
      fromStringOption(name, Formats.DashedFormat.parseDateTime(_), description)

    def intOption(longName: String, description: String): Parser[Option[Int]] = {
      OptionalDef[Int](longName, s => scala.util.Try(s.toInt), description).toParser
    }

    def intRequired(longName: String, description: String): Parser[Int] = {
      RequiredDef[Int](longName, s => scala.util.Try(s.toInt), description).toParser
    }

    def stringOption(longName: String, description: String): Parser[Option[String]] = {
      OptionalDef[String](longName, s => scala.util.Try(s), description).toParser
    }

    def stringRequired(longName: String, description: String): Parser[String] = {
      RequiredDef[String](longName, s => scala.util.Try(s), description).toParser
    }

    def fromStringRequired[A](longName: String, f: String => A, description: String): Parser[A] = {
      RequiredDef[A](longName, s => scala.util.Try(f(s)), description).toParser
    }

    def fromStringList[A](longName: String, f: String => A, description: String): Parser[List[A]] = {
      import Scalaz._
      implicit val listTraverse = implicitly[Traverse[List]]

      def tryFromString(s: String): scala.util.Try[List[A]] = {
        val option: Option[List[A]] = listTraverse.sequence(
          s.split(",").toList.map(piece => scala.util.Try(f(piece)).toOption)
        )
        option match {
          case Some(list) => scala.util.Success(list)
          case None       => scala.util.Failure(new Exception(s"value not parsable: ${s}"))
        }
      }
      RequiredDef[List[A]](longName, tryFromString, description).toParser
    }

    def fromStringOption[A](longName: String, f: String => A, description: String): Parser[Option[A]] = {
      OptionalDef[A](longName, s => scala.util.Try(f(s)), description).toParser
    }
  }

  type ParserResult[A] = (List[ReadError], List[String], Option[ReadValue[A]])
  case class Parser[A](run: Map[String, String] => ParserResult[A]) {

    def runWithArgs(args: Array[String]): ParserResult[A] = runWithArgs(args.toList)

    def runWithArgs(args: List[String]): ParserResult[A] = run(readArgsMap(args))

    def printResults: Unit = {
      val usage = run(Map.empty[String, String])
      println("Usage: ")
      usage._1.foreach(println)
    }
  }

  implicit val parserInstance: Applicative[Parser] = new Applicative[Parser] {
    def point[A](a: => A): Parser[A] = Parser(_ => (List.empty[ReadError], List.empty[String], Some(ReadValue(a))))
    def ap[A, B](fa: => Parser[A])(f: => Parser[A => B]): Parser[B] = {
      Parser[B] { m =>
        val (es1, ss1, rg)  = f.run(m)
        val (es2, ss2, ra)  = fa.run(m)
        val errors = es1 ++ es2
        val sets   = ss1 ++ ss2
        (rg, ra) match {
          case (Some(ReadValue(g)), Some(ReadValue(a)))  => (errors, sets, Some(ReadValue(g(a))))
          case (otherG, otherA)                          => (errors, sets, None)
        }
      }
    }
  }

  sealed trait OptDef[A] {
    def longName: String
    def toParser: Parser[A]
  }
  case class OptionalDef[B](longName: String, reader: String => scala.util.Try[B], desc: String) extends OptDef[Option[B]] {
    def toParser = {
      Parser[Option[B]] { m =>
        readOptional(this)(m) match {
          case ReadValue(v)                          => (List.empty[ReadError], List(s"--$longName = ${v}"), Some(ReadValue(v)))
          case OptionsReadError(longName, string, f) => (List(OptionsReadError(longName, string, f)), List(s"--$longName $desc"), None)
          case NotSpecifiedError(longName)           => (List(NotSpecifiedError(longName)), List(s"--$longName $desc"), None)
        }
      }
    }
  }

  case class RequiredDef[A](longName: String, reader: String => scala.util.Try[A], desc: String) extends OptDef[A] {
    def toParser = {
      Parser { m =>
        readRequired(this)(m) match {
          case ReadValue(v)                          => (List.empty[ReadError], List(s"--$longName = ${v}"), Some(ReadValue(v)))
          case OptionsReadError(longName, string, f) => (List(OptionsReadError(longName, string, f)), List(s"--$longName $desc"), None)
          case NotSpecifiedError(longName)           => (List(NotSpecifiedError(longName)), List(s"--$longName $desc"), None)
        }
      }
    }
  }

  sealed trait OptionReadResult[+A]
  sealed trait ReadError extends OptionReadResult[Nothing]
  case class OptionsReadError(longName: String, value: String, f: Throwable) extends ReadError
  case class NotSpecifiedError(longName: String) extends ReadError
  case class ReadValue[A](a: A) extends OptionReadResult[A]

  def readRequired[A](optDef: RequiredDef[A])(args: Map[String, String]): OptionReadResult[A] = {
    args.get("--" + optDef.longName) match {
      case Some(s) => optDef.reader(s) match {
        case scala.util.Success(v) => ReadValue(v)
        case scala.util.Failure(f) => OptionsReadError(optDef.longName, s, f)
      }
      case None =>
        NotSpecifiedError(optDef.longName)
    }
  }

  def readOptional[A](optDef: OptionalDef[A])(args: Map[String, String]): OptionReadResult[Option[A]] = {
    args.get("--" + optDef.longName) match {
      case Some(s) => optDef.reader(s) match {
        case scala.util.Success(v) => ReadValue(Some(v))
        case scala.util.Failure(f) => OptionsReadError(optDef.longName, s, f)
      }
      case None => ReadValue(None)
    }
  }

  // Only handles k,v no flags yet
  def readArgs(args: List[String]): List[(String, String)] = {
    def readArgsHelper(soFar: List[(String, String)])(as: List[String]): List[(String, String)] = {
      as match {
        case k :: v :: rest => readArgsHelper((k,v) :: soFar)(rest)
        case _              => soFar
      }
    }
    readArgsHelper(List.empty[(String, String)])(args)
  }

  def readArgsMap(args: List[String]): Map[String, String] = readArgs(args).toMap
}
