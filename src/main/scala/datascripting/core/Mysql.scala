package datascripting.core

import scalaz.concurrent.Task
import com.couchbase.client.java._
import scalaz.stream._
import scalaz._
import argonaut._, Argonaut._
import scala.xml._
import scala.concurrent.Future
import scala.concurrent.Future.{successful, failed}
import dispatch._
import com.ning.http.client._
import org.joda.time.DateTime
import org.joda.time.format._



object Mysql {
  import doobie.imports._
  import doobie.contrib.hikari.hikaritransactor._
  import scalaz.concurrent.Task

  val driverClassName = "com.mysql.jdbc.Driver"

  case class MysqlConfig(
    schema: String,
    host: String,
    user: String,
    password: String
  ) {
    val urlProps        = "?jdbcCompliantTruncation=false&useServerPrepStmts=true&cachePrepStmts=true&prepStmtCacheSqlLimit=512&prepStmtCacheSize=500&zeroDateTimeBehavior=round"
    def jdbcUrl         = s"jdbc:mysql://${host}:3306/${schema}${urlProps}"
  }

  def withTransactor[T](config: MysqlConfig)(p: HikariTransactor[Task] => Task[T]): Task[T] = for {
    xa <- HikariTransactor[Task](driverClassName, config.jdbcUrl, config.user, config.password)
    _  <- xa.configure(hx => Task.delay(()))
    r  <- p(xa) ensuring xa.shutdown
  } yield r
}
