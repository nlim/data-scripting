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

object Couch {
  import scalaz._
  import argonaut._, Argonaut._
  import com.couchbase.client.java._
  import com.couchbase.client.java.document._
  import rx.{Observable, Observer}
  import scalaz.concurrent.Task
  import scala.concurrent.duration._

  case class CouchConfig(
    hosts: List[String],
    bucket: String,
    bucketPassword: String
  )

  def toStringStdOutlines[A](toString: A => String): Sink[Task, A] = {
    io.stdOutLines.map { out => (a: A) => out(toString(a)) }
  }

  def withBucket[A](conf: CouchConfig)(f: AsyncBucket => Task[A]): Task[A] = {
    withCluster(conf.hosts) { c =>
      withBucketHelper(c, conf.bucket, conf.bucketPassword)(f)
    }
  }

  def withBucketHelper[A](c: CouchbaseCluster, bucket: String, bucketPassword: String)(f: AsyncBucket => Task[A]): Task[A] = {
    import doobie.imports._
    import scalaz.concurrent.Task

    for {
      b <- Task.delay(Couch.getBucket(c, bucket, bucketPassword))
      r <- f(b).ensuring(Couch.shutdownCouch(b))
    } yield r
  }


  def withCluster[A](hosts: List[String])(f: CouchbaseCluster => Task[A]): Task[A] = {
    import scala.collection.JavaConverters._
    import doobie.imports._
    val env = com.couchbase.client.java.env.DefaultCouchbaseEnvironment.create()
    val hostsJava: java.util.List[String]  = hosts.asJava
    for {
      c <- Task.delay(CouchbaseCluster.create(env, hostsJava))
      r <- f(c).ensuring(Task.delay(shutdownCluster(c)))
    } yield r
  }

  def shutdownCluster(c: CouchbaseCluster): Unit = {
    c.disconnect()
    println("Closed connection to CouchbaseCluster")
  }


  def shutdownCouch(b: AsyncBucket): Task[java.lang.Boolean]= {
    for {
      _ <- Task.delay(println(s"Shutting down Couchbase bucket: $b"))
      r <- TaskUtils.toTask(b.close())
      _ <- Task.delay(s"Done shutting down $b")
    } yield r
  }

  def getClusterAndBucket(bucketName: String, bucketPassword: String) = {
    val cluster     = CouchbaseCluster.create()
    cluster.openBucket(bucketName, bucketPassword).async()
  }

  def getBucket(cluster: CouchbaseCluster, bucketName: String, bucketPassword: String) = {
    cluster.openBucket(bucketName, bucketPassword).async()
  }

  def runOpHelper[B](b: AsyncBucket)(op: AsyncBucket => Observable[B]): Task[B] = {
    val p = scalaz.concurrent.Strategy.DefaultExecutorService
    Task.fork(TaskUtils.toTask(op(b)))(p)
  }

  import com.couchbase.client.deps.io.netty.buffer.Unpooled

  def createRawDoc[D: EncodeJson](key: String, d: D, expiry: Int) = {
    BinaryDocument.create(key, expiry, Unpooled.copiedBuffer(d.asJson.toString.getBytes))
  }
  import scala.language.postfixOps

  val ThirtyDays = 30 days

  def toCouchbaseSeconds(dur: scala.concurrent.duration.Duration) = {
    if (dur > ThirtyDays ){
      ((System.currentTimeMillis() + dur.toMillis) / 1000).toInt
    } else {
      dur.toSeconds.toInt
    }
  }

  def createRawDoc[D: EncodeJson](key: String, d: D, expiry: scala.concurrent.duration.Duration) = {
    BinaryDocument.create(key, toCouchbaseSeconds(expiry), Unpooled.copiedBuffer(d.asJson.toString.getBytes))
  }

  def upsertDoc[D: EncodeJson](b: AsyncBucket)(k: String, d: D, exp: Duration): Task[BinaryDocument] = {
    val doc = createRawDoc(k, d, exp)
    runOpHelper(b)(_.upsert(doc))
  }

  def replaceDoc[D: EncodeJson](b: AsyncBucket)(k: String, d: D, exp: Duration) = {
    val doc = createRawDoc(k, d, exp)
    runOpHelper(b)(_.replace(doc))
  }

  def insertDoc[D: EncodeJson](b: AsyncBucket)(k: String, d: D, exp: Duration) = {
    val doc = createRawDoc(k, d, exp)
    runOpHelper(b)(_.insert(doc))
  }


  def getBytes(doc: BinaryDocument): Array[Byte] = {
    val data = Array.ofDim[Byte](doc.content.readableBytes())
    doc.content().readBytes(data)
    doc.content().release()
    data
  }

  def getJson(b: AsyncBucket)(k: String) = getValue[Json](b)(k)

  def parseBinaryDoc[D: DecodeJson](b: BinaryDocument): Option[D] = {
    val decoder = implicitly[DecodeJson[D]]
    val bytes = getBytes(b)
    Parse.decodeOption[D](new String(bytes))
  }

  def getBinaryDoc(b: AsyncBucket)(k: String): Task[Option[BinaryDocument]] = {
    val lookupTask: Task[BinaryDocument] = runOpHelper(b)(_.get(k, classOf[BinaryDocument]))
    TaskUtils.recoverWithNone(lookupTask)
  }

  def removeDocs(b: AsyncBucket)(ks: List[String]): Task[List[Option[Long]]] = {
    import scalaz.std.list._
    implicitly[Traverse[List]].traverse(ks)(removeDoc(b))
  }

  def removeDoc(b: AsyncBucket)(k: String): Task[Option[Long]] = {
    runOpHelper(b)(_.remove(k)).map(doc => Option(doc.cas))
  }

  def getValue[D: DecodeJson](b: AsyncBucket)(k: String): Task[Option[D]] = {
    getBinaryDoc(b)(k).map(bOpt => bOpt.flatMap(parseBinaryDoc[D]) )
  }
}


