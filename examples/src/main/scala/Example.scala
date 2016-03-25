package datascripting.examples
import datascripting.core._

import Options._
import Options.Parser._

import scalaz.concurrent.Task
import com.couchbase.client.java._
import com.couchbase.client.java.document.BinaryDocument
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
import doobie.imports._


object Example extends ScriptMain {

  type Input = (Mysql.MysqlConfig, Couch.CouchConfig, DateTime, DateTime, Int)

  def argsParser = {
    Applicative[Parser].tuple5(
      Options.Parser.mysqlConfigParser,
      Options.Parser.couchConfigParser,
      Options.Parser.dateRequired("from", "From Date"),
      Options.Parser.dateRequired("to", "To Date"),
      Options.Parser.intRequired("maxCouchQueries", "Number of Couch Queries at once")
    )
  }


  def mainTask(t: Input): Task[Unit] = {
    val (mysqlConfig, couchConfig, fromDate, toDate, maxCouchQueries) = t

    Mysql.withTransactor(mysqlConfig) { xa =>
      Couch.withBucket(couchConfig) { b =>
        val p: Process[Task, UserDB.User]  = UserDB.userQuery(fromDate, toDate).process.transact(xa)
        val p2: Process[Task, Process[Task, BinaryDocument]] = {
          p.map(u => Process.eval(Couch.upsertDoc(b)(s"u_${u.id}", u, 90 days)))
        }

        val p3: Process[Task, String] = merge.mergeN(maxCouchQueries)(p2).map(b => b.cas.toString)

        p3.to(io.stdOutLines).run
      }
    }
  }
}


object UserDB {
  import doobie.contrib.hikari.hikaritransactor._
  import scalaz.concurrent.Task

  case class User(id: Long, createDate: Long, name: String)

  def userQuery(fromDate: DateTime, toDate: DateTime) = {
    val from = Formats.DashedFormat.print(fromDate)
    val to   = Formats.DashedFormat.print(toDate)
    sql"""
      select id, createDate, name
      where createDate > date($from) and createDate < date($to)
    """.query[User]
  }


  implicit val codecUser: CodecJson[User] = casecodec3(User.apply, User.unapply)(
    "id", "createDate", "name"
  )
}


