package de.welt.contentapi.client.services.configuration

import java.time.Instant
import javax.inject.{Inject, Singleton}

import com.google.common.base.Stopwatch
import de.welt.contentapi.client.services.contentapi.LegacySectionService
import de.welt.contentapi.client.services.s3.S3
import de.welt.contentapi.core.models._
import de.welt.contentapi.core.traits.Loggable
import play.api.cache.CacheApi
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.{Environment, Mode}

import scala.concurrent.duration._

trait SectionConfigurationService {

  def getChannelByPath(path: String)(implicit env: Env): Option[Channel]
  def updateChannel(channel: Channel, updatedChannelData: ChannelData, user: String)(implicit env: Env): Option[Channel]
  def syncWithLegacy(): Unit

}

@Singleton
class SectionConfigurationServiceImpl @Inject()(funkConfig: ContentClientConfig,
                                                s3: S3,
                                                environment: Environment,
                                                legacySectionService: LegacySectionService,
                                                cache: CacheApi)
  extends SectionConfigurationService with Loggable {

  override def getChannelByPath(path: String)(implicit env: Env): Option[Channel] = root.findByPath(path)

  override def updateChannel(channel: Channel, updatedChannelData: ChannelData, user: String)(implicit env: Env): Option[Channel] = {

    // update channel (lastModified), currently adData and metadata allowed only
    channel.data = channel.data.copy(adData = updatedChannelData.adData, metadata = updatedChannelData.metadata)
    channel.lastModifiedDate = Instant.now.toEpochMilli
    channel.metadata = Some(ChannelMetadataNew(user, Instant.now.toEpochMilli))

    log.info(s"$channel changed by $user")

    // save changes
    save

    // reload changes from s3
    val freshRootNode = root
    val updatedChannel = freshRootNode.findByPath(channel.id.path)
    log.debug(s"Updated Channel from fresh s3 data $updatedChannel")
    updatedChannel
  }

  override def syncWithLegacy(): Unit = {
    def mergeAndSave(updates: Channel, env: Env): ChannelUpdate = {
      val r = root(env)
      val mergeResult = r.merge(updates)
      saveChannel(r)(env)
      mergeResult
    }
    log.info(s"[Sync] starting sync. ")
    val stopwatch = Stopwatch.createStarted()
    val updates = legacySectionService.getSectionData.toChannel

    mergeAndSave(updates, Preview)
    val mergeUpdate = mergeAndSave(updates, Live)

    log.info(s"[Sync] Done syncing with legacy, found the following changes: $mergeUpdate")
    log.info(s"[Sync] took ${stopwatch.stop.toString}")
  }

  private def objectKeyForEnv(env: Env) = environment.mode match {
    case Mode.Prod ⇒ s"janus2/prod/${env.toString}/config.json"
    case _ ⇒ s"janus2/dev/${env.toString}/config.json"
  }

  def root(implicit env: Env): Channel = cache.getOrElse(env.toString, 10.minutes) {
    import de.welt.contentapi.core.models.reads.FullChannelReads.channelReads

    val maybeData: Option[String] = s3.get(funkConfig.aws.s3.janus.bucket, objectKeyForEnv(env))

    if (maybeData.isEmpty) {
      log.warn("No data found in s3 bucket, creating new data set from scratch.")
      val root = legacySectionService.getSectionData.toChannel

      saveChannel(root)(Preview)
      saveChannel(root)(Live)
      root
    } else {

      val channels = maybeData
        .map { data ⇒ Json.parse(data).validate[Channel] }
        .flatMap {
          case JsSuccess(v, _) ⇒
            Some(v)
          case err@JsError(_) ⇒
            log.warn(err.toString)
            None
        }.getOrElse {
        throw new IllegalStateException("no channel data")
      }

      channels
    }
  }

  private def saveChannel(ch: Channel)(implicit env: Env) = {
    import de.welt.contentapi.core.models.writes.FullChannelWrites.channelWrites

    ch.applyChannelUpdates()

    val serializedChannelData = Json.toJson(ch).toString

    log.info(s"saving channel tree to s3 -> ${objectKeyForEnv(env)}")

    s3.putPrivate(funkConfig.aws.s3.janus.bucket, objectKeyForEnv(env), serializedChannelData, "application/json")

    log.debug("Invalidating cache.")
    cache.remove(env.toString)
  }

  private def save(implicit env: Env) = {
    saveChannel(root)
  }
}




