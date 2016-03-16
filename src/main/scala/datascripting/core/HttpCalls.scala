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
import java.io.FileInputStream

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

  def getHttpWithCert(password: String, certFileName: String): Task[Http] = {
    val inT = Task.delay { new FileInputStream(certFileName) }
    inT map { in =>
      getHttp.configure{ builder =>
        val certPassword = password.toCharArray
        // Load KeyStore from cert file
        val ks = java.security.KeyStore.getInstance("PKCS12")
        ks.load(in, certPassword)
        in.close()
        // KeyManager is initialized with the loaded certificates
        val kmf = javax.net.ssl.KeyManagerFactory.getInstance("SunX509")
        kmf.init(ks, certPassword)
        // sslContext is initialized with the KeyManager above,
        // default TrustManager and SecureRandom
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        // null to use default TrustManager and SecureRandom
        sslContext.init(kmf.getKeyManagers, null, null)

        builder.setSSLContext(sslContext)
      }
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
