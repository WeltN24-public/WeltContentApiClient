package de.welt.contentapi.core.models

import de.welt.welt.meta.ChannelHelper
import org.scalatestplus.play.PlaySpec

class ChannelUpdateTest extends PlaySpec {

  trait Fixture {

    val channels = Seq(ChannelHelper.emptyWithId(1))

    val update = ChannelUpdate()
  }

  "ChannelUpdate" must {
    "merge added to update" in new Fixture {
      update.merge(ChannelUpdate(added = channels)) must be(ChannelUpdate(added = channels))
    }
    "merge deleted to update" in new Fixture {
      update.merge(ChannelUpdate(deleted = channels)) must be(ChannelUpdate(deleted = channels))
    }
    "merge moved to update" in new Fixture {
      update.merge(ChannelUpdate(moved = channels)) must be(ChannelUpdate(moved = channels))
    }
    "merge lists of updates" in new Fixture {
      val subUpdates = Seq(
        ChannelUpdate(added = channels),
        ChannelUpdate(deleted = channels),
        ChannelUpdate(moved = channels)
      )

      update.merge(subUpdates) must be(ChannelUpdate(
        added = channels,
        deleted = channels,
        moved = channels
      ))
    }

    "deduplicate during merge" in new Fixture {
      val subUpdates = Seq(
        ChannelUpdate(added = channels, deleted = channels),
        ChannelUpdate(deleted = channels, moved = channels),
        ChannelUpdate(moved = channels, added = channels)
      )

      update.merge(subUpdates) must be(ChannelUpdate(
        added = channels,
        deleted = channels,
        moved = channels
      ))
    }

    "keep the original lists from the root update" in new Fixture {

      val otherChannels = Seq(ChannelHelper.emptyWithId(2))
      val subUpdates = Seq(
        ChannelUpdate(added = channels),
        ChannelUpdate(deleted = channels),
        ChannelUpdate(moved = channels)
      )

      ChannelUpdate(otherChannels, otherChannels, otherChannels).merge(subUpdates) must be(ChannelUpdate(
        added = otherChannels ++ channels,
        deleted = otherChannels ++ channels,
        moved = otherChannels ++ channels
      ))
    }
  }

}
