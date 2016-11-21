package de.welt.contentapi.raw_client.models

import java.time.Instant

import de.welt.contentapi.raw.models.legacy.{ApiChannel, ApiChannelData, ChannelId}

case class SdpSectionData(url: String,
                          displayName: String,
                          lastModifiedDate: Option[String],
                          children: Seq[SdpSectionData],
                          id: Long) {

  private def defineAdTag(data: ApiChannelData) = data.copy(adData = data.adData.copy(definesAdTag = true))

  def toChannel: ApiChannel = {
    val root = transform
    // initially set hasAdTag to true for level 0 & 1 of the section tree
    root.data = defineAdTag(root.data)
    root.children.foreach { child ⇒ child.data = defineAdTag(child.data) }

    // set the parent-relation for all elements
    root.updateParentRelations()
    root
  }

  private def transform: ApiChannel = ApiChannel(
    id = ChannelId(path = url, ece = id),
    data = ApiChannelData(displayName),
    children = children.map(_.transform),
    lastModifiedDate = lastModifiedDate match {
      case Some("") ⇒ Instant.now.toEpochMilli
      case Some(s) ⇒ s.toLong
      case _ ⇒ Instant.now.toEpochMilli
    }
  )
}

object SdpSectionDataReads {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import play.api.libs.json._

  implicit lazy val s3SectionDataReads: Reads[SdpSectionData] = (
    (JsPath \ "url").read[String] and
      (JsPath \ "displayName").read[String] and
      (JsPath \ "lastModifiedDate").readNullable[String] and
      (JsPath \ "children").lazyRead(Reads.seq[SdpSectionData](s3SectionDataReads)) and
      (JsPath \ "id").read[String].map(_.toLong)
    ) (SdpSectionData)
}
