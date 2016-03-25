package datascripting.examples

import datascripting.core._
import com.couchbase.client.java._
import com.couchbase.client.java.document._
import scalaz.concurrent.Task
import scalaz.stream._
import doobie.imports._
import argonaut._, Argonaut._
import java.util.concurrent.ExecutorService
import dispatch._
import com.ning.http.client._
import org.joda.time.DateTime
import org.joda.time.format._
import Options._
import Options.Parser._
import scalaz._
import doobie.contrib.hikari.hikaritransactor._
import scala.concurrent.duration._
import scala.language.postfixOps

object Example2 extends ScriptMain {
  type Input = (List[String], Int)

  type RedditLine = (String, String, DateTime)

  val argsParser = Applicative[Parser].tuple2(
    Options.Parser.fromStringList("subreddits", identity, "SubReddits"),
    Options.Parser.intRequired("takeAmount", "Top Posts Number")
  )

  def mainTask(t: Input): Task[Unit] = {
    val (subreddits, takeAmount) = t
    val http = HttpCalls.getHttp
    val ex   = scala.concurrent.ExecutionContext.Implicits.global

    def getPosts(subreddit: String): Process[Task, RedditLine] = for {
      seq <- Process.eval(getRss(http, ex)(subreddit))
      line <- Process.emitAll(seq)
    } yield line


    val posts  = subreddits.map(getPosts)
    val merged = multiMerge(posts, mergeSort(lineLastUpdated))

    val lines = merged map {
      case (sub, title, updated) => s"$sub: $title, ${Formats.IsoFormat.print(updated)}"
    }

    lines.take(takeAmount).to(io.stdOutLines).run
  }

  def mergeSort[T](greaterThan: Function2[T, T, Boolean]): Tee[T, T, T] = {
     Process.repeat {
       for {
         t1 <- Process.awaitL[T]
         t2 <- Process.awaitR[T]
         ts = if (greaterThan(t1,t2)) Seq(t1, t2) else Seq(t2, t1)
         r <- Process.emitAll(ts)
       } yield r
     }
  }

  // Just works for Lists of upate to 4 Processes
  def multiMerge[T](list: List[Process[Task, T]], merger: Tee[T, T, T]): Process[Task, T] = {
    list match {
      case Nil              => Process.halt
      case p1 :: Nil        => p1
      case p1 :: p2 :: Nil  =>
        p1.tee(p2)(merger)
      case p1 :: p2 :: p3 :: Nil =>
        val x = p1.tee(p2)(merger)
        x.tee(p3)(merger)
      case p1 :: p2 :: p3 :: p4 :: rest =>
        val x = p1.tee(p2)(merger)
        val y = p3.tee(p4)(merger)
        x.tee(y)(merger)
    }
  }

  def lineLastUpdated(r1: RedditLine, r2: RedditLine): Boolean = {
    r1._3.getMillis > r2._3.getMillis
  }

  def parseIsoDate(s: String) = scala.util.Try(Formats.IsoFormat.parseDateTime(s)).toOption

  def getRss(http: Http, ex: scala.concurrent.ExecutionContext)(subreddit: String): Task[Seq[RedditLine]] = {
    val req = url(s"https://www.reddit.com/r/$subreddit/new/.rss").GET
    HttpCalls.runRequest(http, ex)(req) map { case (c, data) =>
      val elem = xml.XML.loadString(new String(data.getBytes, "UTF-8"))
      val entries = elem \ "entry"
      entries map { entry =>
        for {
          title   <- (entry \ "title").headOption.map(_.text)
          updated <- (entry \ "updated").headOption.map(_.text).flatMap(parseIsoDate)
          content <- (entry \ "content").headOption.map(_.text)
        } yield (subreddit, title, updated)
      } flatten
    }
  }
}

