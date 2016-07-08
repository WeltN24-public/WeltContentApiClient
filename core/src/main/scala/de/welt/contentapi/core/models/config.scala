package de.welt.contentapi.core.models

import de.welt.contentapi.core.models._
import de.welt.contentapi.core.models.Query._
import de.welt.contentapi.core.models.Datasource._
import play.api.libs.functional.syntax._
import play.api.libs.json._

sealed trait Env

case object Preview extends Env

case object Live extends Env

case object UndefinedEnv extends Env

object Env {
  def apply(env: String): Env = env match {
    case "preview" ⇒ Preview
    case "live" ⇒ Live
    case _ ⇒ throw new IllegalArgumentException(s"Not a valid env: $env. Allowed values are 'preview' and 'live'")
  }
}

object reads {

  object FullChannelReads {

    import SimpleFormats._
    import play.api.libs.functional.syntax._
    import play.api.libs.json._

    implicit lazy val channelReads: Reads[Channel] = (
      (__ \ "id").read[ChannelId] and
        (__ \ "data").read[ChannelData] and
        (__ \ "stages").read[Seq[Stage]] and
        (__ \ "parent").lazyRead(Reads.optionWithNull(channelReads)) and
        (__ \ "children").lazyRead(Reads.seq[Channel](channelReads)) and
        (__ \ "hasChildren").read[Boolean] and
        (__ \ "lastModifiedDate").read[Long] and
        (__ \ "metadata").readNullable[ChannelMetadataNew]
      ) (Channel)
  }

  object PartialChannelReads {

    import SimpleFormats._

    implicit lazy val noChildrenReads: Reads[Channel] = new Reads[Channel] {
      override def reads(json: JsValue): JsResult[Channel] = json match {
        case JsObject(underlying) ⇒ (for {
          id ← underlying.get("id").map(_.as[ChannelId])
          data ← underlying.get("data").map(_.as[ChannelData])
          metadata ← underlying.get("metadata").map(_.as[ChannelMetadataNew])
        } yield JsSuccess(Channel(id = id, data = data, metadata = Some(metadata))))
          .getOrElse(JsError("Could not validate json [something is missing]. " + Json.prettyPrint(json)))

        case err@_ ⇒ JsError(s"expected js-object, but was $err")
      }
    }

  }

}

object writes {

  object FullChannelWrites {

    import SimpleFormats._
    import play.api.libs.functional.syntax._
    import play.api.libs.json._

    implicit lazy val channelWrites: Writes[Channel] = (
      (__ \ "id").write[ChannelId] and
        (__ \ "data").write[ChannelData] and
        (__ \ "stages").write[Seq[Stage]] and
        (__ \ "parent").lazyWrite(Writes.optionWithNull(PartialChannelWrites.writeChannelAsNull)) and // avoid loops
        (__ \ "children").lazyWrite(Writes.seq[Channel](channelWrites)) and
        (__ \ "hasChildren").write[Boolean] and
        (__ \ "lastModifiedDate").write[Long] and
        (__ \ "metadata").writeNullable[ChannelMetadataNew]
      ) (unlift(Channel.unapply))

  }

  object PartialChannelWrites {

    import SimpleFormats._

    implicit lazy val noChildrenWrites = new Writes[Channel] {
      override def writes(o: Channel): JsValue = JsObject(Map(
        "id" → Json.toJson(o.id),
        "lastModifiedDate" → JsNumber(o.lastModifiedDate),
        "hasChildren" → JsBoolean(o.hasChildren),
        "data" → Json.toJson(o.data),
        "stages" → Json.toJson(o.stages),
        "metadata" → Json.toJson(o.metadata)
      ))
    }

    implicit lazy val oneLevelOfChildren: Writes[Channel] = (
      (__ \ "id").write[ChannelId] and
        (__ \ "data").write[ChannelData] and
        (__ \ "stages").write[Seq[Stage]] and
        (__ \ "parent").lazyWrite(Writes.optionWithNull(noChildrenWrites)) and
        (__ \ "children").lazyWrite(Writes.seq[Channel](noChildrenWrites)) and
        (__ \ "hasChildren").write[Boolean] and
        (__ \ "lastModifiedDate").write[Long] and
        (__ \ "metadata").writeNullable[ChannelMetadataNew]
      ) (unlift(Channel.unapply))

    implicit lazy val writeChannelAsNull: Writes[Channel] = new Writes[Channel] {
      override def writes(o: Channel): JsValue = JsNull
    }
  }

}


object SimpleFormats {
  implicit lazy val idFormat: Format[ChannelId] = Json.format[ChannelId]
  implicit lazy val adFormat: Format[ChannelAdData] = Json.format[ChannelAdData]
  implicit lazy val metaDataFormat: Format[ChannelMetadata] = Json.format[ChannelMetadata]
  implicit lazy val metaDataNewFormat: Format[ChannelMetadataNew] = Json.format[ChannelMetadataNew]
  implicit lazy val dataFormat: Format[ChannelData] = Json.format[ChannelData]
  implicit lazy val stageFormat: Format[Stage] = Json.format[Stage]
  // Data Sources
  implicit lazy val datasourceFormat: Format[Datasource] = Json.format[Datasource]
  implicit lazy val searchSourceFormat: Format[SearchSource] = Json.format[SearchSource]
  implicit lazy val curatedSourceFormat: Format[CuratedSource] = Json.format[CuratedSource]
  // SearchApiFilters
  implicit lazy val queryFormat: Format[Query] = Json.format[Query]
  implicit lazy val flagQueryFormat: Format[FlagQuery] = Json.format[FlagQuery]
  implicit lazy val sectionQueryFormat: Format[SectionQuery] = Json.format[SectionQuery]
  implicit lazy val subtypeQueryFormat: Format[SubTypeQuery] = Json.format[SubTypeQuery]
  implicit lazy val typeQueryFormat: Format[TypeQuery] = Json.format[TypeQuery]

}


