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


object HttpCalls {
  // A Task that runs a Dispatch request and returns the HTTP statusCode and the responseBody
  def runRequest(h: dispatch.Http, ex: scala.concurrent.ExecutionContext)(req: Req): Task[(Int, String)] = {
    runRequestHelper(h, ex)(req).map(r => (r.getStatusCode, r.getResponseBody))
  }

  def runRequestHelper(h: dispatch.Http, ex: scala.concurrent.ExecutionContext)(req: Req): Task[com.ning.http.client.Response] = {
    def runReq(h: dispatch.Http)(req: Req) =  h(req > {r => r})(ex)
    toTask(ex)(runReq(h)(req))
  }

  def toTask[T](ex: scala.concurrent.ExecutionContext)(ft: => Future[T]): Task[T] = {
    import scalaz._
    import scalaz.Scalaz._
    Task.async { register =>
      ft.onComplete({
        case scala.util.Success(v) => register(\/-(v))
        case scala.util.Failure(ex) => register(-\/(ex))
      })(ex)
    }
  }

  def getHttp = {
    Http().configure{ c =>
      c.setMaximumConnectionsPerHost(100)
        .setMaximumConnectionsTotal(500)
        .setAllowPoolingConnection(true)
      }
  }
}
