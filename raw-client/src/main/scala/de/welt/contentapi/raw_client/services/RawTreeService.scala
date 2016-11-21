package de.welt.contentapi.raw_client.services

import javax.inject.Inject

import de.welt.contentapi.core.client.services.s3.S3Client
import de.welt.contentapi.raw.models.RawChannel
import de.welt.contentapi.utils.Loggable
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsError, JsSuccess, Json}
import scala.concurrent.duration._

trait RawTreeService {
  def get: Option[RawChannel]
}

class RawTreeServiceImpl @Inject()(s3Client: S3Client, config: Configuration, cache: CacheApi) extends RawTreeService with Loggable {

  val bucket = config.getString("welt.aws.s3.rawTree.bucket")
    .getOrElse(throw config.reportError("welt.aws.s3.rawTree.bucket", "welt.aws.s3.rawTree.bucket bucket not configured"))
  val file = config.getString("welt.aws.s3.rawTree.file")
    .getOrElse(throw config.reportError("welt.aws.s3.rawTree.file", "welt.aws.s3.rawTree.file file not configured"))

  override def get: Option[RawChannel] = {

    cache.getOrElse("rawChannelData", 5.minutes) {
      s3Client.get(bucket, file).flatMap { tree ⇒
        import de.welt.contentapi.raw.models.RawReads._
        Json.parse(tree).validate[RawChannel] match {
          case s: JsSuccess[RawChannel] ⇒ s.asOpt
          case e: JsError ⇒
            log.error(f"JsError parsing S3 file: '$bucket%s/$file%s'. " + JsError.toJson(e).toString())
            None
        }
      }
    }
  }
}