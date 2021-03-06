package de.welt.contentapi.core.models

import java.time.Instant

import de.welt.contentapi.core.traits.Loggable
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.annotation.tailrec

object config extends Loggable {

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

  case class Channel(id: ChannelId,
                     var data: ChannelData,
                     //                   stages: Seq[Stage] = Seq.empty,
                     var parent: Option[Channel] = None,
                     var children: Seq[Channel] = Seq.empty,
                     var hasChildren: Boolean = false,
                     var lastModifiedDate: Long = Instant.now.toEpochMilli) {

    hasChildren = children.nonEmpty

    final def updateParentRelations(newParent: Option[Channel] = None): Unit = {

      this.parent = newParent
      children.foreach(_.updateParentRelations(Some(this)))

    }

    final def findByEce(ece: Long): Option[Channel] = {
      if (id.ece == ece) {
        Some(this)
      } else {
        children.flatMap { ch ⇒ ch.findByEce(ece) }.headOption
      }
    }

    def findByPath(search: String): Option[Channel] = findByPath(
      search.split('/').filter(_.nonEmpty).toList match {
        case Nil ⇒ Nil
        case head :: tail ⇒ tail.scanLeft(s"/$head/")((path, s) ⇒ path + s + "/")
      }
    )

    private def findByPath(sectionPath: Seq[String]): Option[Channel] = {
      sectionPath match {
        case Nil ⇒
          Some(this)
        case head :: Nil ⇒
          children.find(_.id.path == head)
        case head :: tail ⇒
          children.find(_.id.path == head).flatMap(_.findByPath(tail))
      }
    }

    override def toString: String = s"Channel(${id.path})"

    @tailrec
    final def root: Channel = parent match {
      case Some(p) ⇒ p.root
      case None => this
    }

    def diff(other: Channel): ChannelUpdate = {

      if (this != other) {
        log.debug(s"Cannot diff($this, $other, because they are not .equal()")
        return ChannelUpdate(Seq.empty, Seq.empty, Seq.empty)
      }

      val bothPresentIds = this.children.map(_.id).intersect(other.children.map(_.id))
      val updatesFromChildren = bothPresentIds.flatMap { id ⇒
        val tupleOfMatchingChannels = this.children.find(_.id == id).zip(other.children.find(_.id == id))

        tupleOfMatchingChannels.map { tuple ⇒
          tuple._1.diff(tuple._2)
        }
      }
      // elements that are no longer in `other.children`
      val deletedByOther = this.children.diff(other.children)
      // additional elements from `other.children`
      val addedByOther = other.children.diff(this.children)

      log.debug(s"[$this] added locally: $addedByOther")
      log.debug(s"[$this] deleted locally: $deletedByOther")

      val moved = {
        lazy val thisRoot = this.root

        // if we can find it in our tree, it hasn't been added but only moved
        val notAddedButMoved = addedByOther.filter { elem ⇒ thisRoot.findByEce(elem.id.ece).isDefined }
        log.debug(s"[$this] not added but moved: $notAddedButMoved")

        lazy val otherRoot = other.root
        // if we can find the deleted elem, it has been moved
        val notDeletedButMoved = deletedByOther.filter { elem ⇒ otherRoot.findByEce(elem.id.ece).isDefined }
        log.debug(s"[$this] not deleted but moved: $notDeletedButMoved")

        notAddedButMoved ++ notDeletedButMoved
      }
      log.debug(s"[$this] moved: $moved")

      val deleted = deletedByOther.diff(moved)
      val added = addedByOther.diff(moved)

      log.debug(s"[$this] deleted globally: $deleted")
      log.debug(s"[$this] added globally: $added")

      val u = ChannelUpdate(added, deleted, moved).merge(updatesFromChildren)
      log.debug(s"[$this] Changes: $u\n\n")
      u
    }

    def merge(other: Channel): ChannelUpdate = {

      val channelUpdate = diff(other)

      channelUpdate.deleted.foreach { deletion ⇒
        deletion.parent.foreach { parent ⇒
          parent.children = parent.children.diff(Seq(deletion))
        }
      }

      channelUpdate.added.foreach { addition ⇒
        this.children = this.children :+ addition
      }

      channelUpdate.moved.foreach { moved ⇒
        // remove from current parent
        moved.parent.foreach { parent ⇒
          parent.children = parent.children.diff(Seq(moved))
        }
        // add to new parent
        val newParentId = other.findByEce(moved.id.ece)
          .flatMap(_.parent)
          .map(_.id.ece)

        newParentId.foreach { parentId ⇒
          root.findByEce(parentId).foreach { newParent ⇒
            newParent.children = newParent.children :+ moved
          }
        }
      }
      // for logging
      channelUpdate
    }

    override def equals(obj: scala.Any): Boolean = obj match {
      case Channel(otherId, _, _, _, _, _) ⇒ this.id == otherId
      case _ ⇒ false
    }

    override def hashCode(): Int = this.id.hashCode()
  }

  case class ChannelId(path: String, isVirtual: Boolean = false, ece: Long = -1) {

    override def equals(obj: scala.Any): Boolean = obj match {
      case ChannelId(_, _, otherEce) ⇒ this.ece == otherEce
      case _ ⇒ false
    }

    override def hashCode(): Int = ece.hashCode()
  }

  case class ChannelUpdate(added: Seq[Channel] = Seq.empty, deleted: Seq[Channel] = Seq.empty, moved: Seq[Channel] = Seq.empty) {
    def merge(other: ChannelUpdate): ChannelUpdate = ChannelUpdate(
      added = (added ++ other.added).distinct,
      deleted = (deleted ++ other.deleted).distinct,
      moved = (moved ++ other.moved).distinct
    )

    /** merge all the updates into this */
    def merge(updates: Seq[ChannelUpdate]): ChannelUpdate = updates.foldLeft(this)((acc, update) => acc.merge(update))
  }


  case class ChannelData(label: String,
                         adData: ChannelAdData = ChannelAdData(),
                         metadata: ChannelMetadata = ChannelMetadata()
                        )

  case class ChannelMetadata(data: Map[String, String] = Map.empty)

  case class ChannelAdData(definesAdTag: Boolean = false)

  case class Stage(maxArticles: Int = 24,
                   sources: Seq[Source],
                   layout: Option[String] = None, // fixed, dynamic todo
                   lazyLoaded: Boolean = false,
                   headline: Option[String] = None,
                   path: Option[String] = None) {

  }

  object Stage {

    def curatedStage(id: ChannelId, count: Int = 4) =
      Stage(
        sources = Seq(CuratedSource(id, 4))
      )

    def highlightStage(id: ChannelId, count: Int = 3) =
      Stage(
        headline = Some("Highlights"),
        sources = Seq(HighlightsSource(id, count))
      )

    def defaultStage(id: ChannelId): Stage =
      Stage(
        sources = Seq(DefaultSource(id))
      )
  }

  trait Source {
    def typ: String

    def count: Int

    def query: String

    def id: ChannelId

    def flags: Set[String] = Set.empty
  }


  case class CuratedSource(override val id: ChannelId,
                           override val count: Int = 5) extends Source {
    override val typ: String = "curated"
    override val query: String = id.path
  }

  case class HighlightsSource(override val id: ChannelId,
                              override val count: Int = 3) extends Source {

    override val typ: String = "highlights"
    override val query: String = id.path
    override val flags: Set[String] = Set("highlight")
  }


  case class DefaultSource(override val id: ChannelId,
                           override val count: Int = 3) extends Source {
    override val typ: String = "search"
    override val query: String = id.path
  }

  case class ChannelWithoutChildren(id: ChannelId, lastModifiedDate: Long, hasChildren: Boolean, data: ChannelData)

  object FullChannelWrites {

    import play.api.libs.functional.syntax._
    import play.api.libs.json._
    import config.SimpleFormats._

    implicit lazy val channelWrites: Writes[Channel] = (
      (__ \ "id").write[ChannelId] and
        (__ \ "data").write[ChannelData] and
        (__ \ "parent").lazyWrite(Writes.optionWithNull(PartialChannelWrites.writeChannelAsNull)) and // avoid loops
        (__ \ "children").lazyWrite(Writes.seq[Channel](channelWrites)) and
        (__ \ "hasChildren").write[Boolean] and
        (__ \ "lastModifiedDate").write[Long]
      ) (unlift(Channel.unapply))

  }

  object PartialChannelWrites {

    import config.SimpleFormats._

    implicit lazy val noChildrenWrites = new Writes[Channel] {
      override def writes(o: Channel): JsValue = JsObject(Map(
        "id" → Json.toJson(o.id),
        "lastModifiedDate" → JsNumber(o.lastModifiedDate),
        "hasChildren" → JsBoolean(o.hasChildren),
        "data" → Json.toJson(o.data)
      ))
    }

    implicit lazy val oneLevelOfChildren: Writes[Channel] = (
      (__ \ "id").write[ChannelId] and
        (__ \ "data").write[ChannelData] and
        (__ \ "parent").lazyWrite(Writes.optionWithNull(noChildrenWrites)) and
        (__ \ "children").lazyWrite(Writes.seq[Channel](noChildrenWrites)) and
        (__ \ "hasChildren").write[Boolean] and
        (__ \ "lastModifiedDate").write[Long]
      ) (unlift(Channel.unapply))

    implicit lazy val writeChannelAsNull: Writes[Channel] = new Writes[Channel] {
      override def writes(o: Channel): JsValue = JsNull
    }
  }

  object WithChildrenReads {

    import play.api.libs.functional.syntax._
    import play.api.libs.json._
    import config.SimpleFormats._

    implicit lazy val channelReads: Reads[Channel] = (
      (__ \ "id").read[ChannelId] and
        (__ \ "data").read[ChannelData] and
        (__ \ "parent").lazyRead(Reads.optionWithNull(channelReads)) and
        (__ \ "children").lazyRead(Reads.seq[Channel](channelReads)) and
        (__ \ "hasChildren").read[Boolean] and
        (__ \ "lastModifiedDate").read[Long]
      ) (Channel)
  }

  object SimpleFormats {
    implicit lazy val idFormat: Format[ChannelId] = Json.format[ChannelId]
    implicit lazy val adFormat: Format[ChannelAdData] = Json.format[ChannelAdData]
    implicit lazy val metaDataFormat: Format[ChannelMetadata] = Json.format[ChannelMetadata]
    implicit lazy val dataFormat: Format[ChannelData] = Json.format[ChannelData]
    implicit lazy val channelWithoutChildrenFormat: Format[ChannelWithoutChildren] = Json.format[ChannelWithoutChildren]
  }

}
