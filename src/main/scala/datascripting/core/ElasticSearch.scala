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


object ElasticSearch {
  case class Source[T](source: T)
  case class Hits[T](total: Int, hitList: List[Source[T]])
  case class OuterResult[T](took: Int, hits: Hits[T])

  implicit def decodeSource[T: DecodeJson]: DecodeJson[Source[T]] = jdecode1L(Source.apply[T])("_source")
  implicit def decodeHits[T: DecodeJson]: DecodeJson[Hits[T]] = jdecode2L(Hits.apply[T])("total", "hits")
  implicit def decodeOuter[T: DecodeJson]: DecodeJson[OuterResult[T]] = jdecode2L(OuterResult.apply[T])("took", "hits")
}
