package datascripting.core

import scalaz.concurrent.Task
import scalaz.stream._
import argonaut._, Argonaut._
import scala.xml._
import scala.concurrent.Future
import scala.concurrent.Future.{successful, failed}
import java.util.concurrent.ExecutorService
import dispatch._
import com.ning.http.client._
import org.joda.time.DateTime
import org.joda.time.format._

object TaskUtils {

  def timeProgram(program: Task[Unit]): Task[Unit] = for {
    b <- Task.delay(System.currentTimeMillis)
    _ <- program
    a <- Task.delay(System.currentTimeMillis)
    _ <- Task.delay(println(s"Seconds: ${(a - b) / 1000}"))
  } yield ()


  def days(from: DateTime, to: DateTime): Process[Task, DateTime] = {
    val result = Process.unfold[DateTime, DateTime](from) { d =>
      if (d.getMillis <= to.getMillis) {
        Some((d, d.plusDays(1)))
      } else {
        None
      }
    }
    result.evalMap(d => Task.now(d))
  }


  import scalaz._
  import argonaut._, Argonaut._
  import com.couchbase.client.java._
  import com.couchbase.client.java.document._
  import rx.{Observable, Observer}
  import scalaz.concurrent.Task


  /**

   Observable to Task conversion

   This only works on Observables where we expect
   one of the following call sequences.
   - onNext, onCompleted
   - onError, onCompleted
   - onCompleted

   When the callback 'cb' is run once, then there will no longer be a reference to
   the Observable and the Observer, so they get GC'd.

   (We could also combine this with a default timeout)

  */


  def observerCallback[T](o: Observable[T])(cb: Throwable \/ T => Unit): Unit = {
    val observer = new Observer[T] {
      var hasResponded = false
      def onNext(t: T) = {
        cb(\/-(t))
        hasResponded = true
      }
      def onError(e: Throwable) = {
        cb(-\/(e))
        hasResponded = true
      }
      def onCompleted = {
        if (hasResponded) {
          ()
        } else {
          cb(-\/(new java.util.NoSuchElementException))
        }
      }
    }
    o.subscribe(observer)
  }

  def toTask[T](o: Observable[T]): Task[T] = {
    Task.async(observerCallback(o))
  }

  def recoverWithNone[T](t: Task[T]): Task[Option[T]] = {
    t.map(Some(_)).handle {
      case nsee: java.util.NoSuchElementException => None
    }
  }
}


