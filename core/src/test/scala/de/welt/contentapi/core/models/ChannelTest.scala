package de.welt.contentapi.core.models

import de.welt.welt.meta.ChannelHelper
import org.scalatestplus.play.PlaySpec

class ChannelTest extends PlaySpec {

  trait Fixture {

    /** CHILD 1 */
    val child1Data = ChannelData(label = "child1", adData = ChannelAdData(true))
    val child1 = ChannelHelper.emptyWithIdAndData(1, child1Data)

    /** CHILD 2 */
    val child2Data = ChannelData(label = "child2", adData = ChannelAdData(true))
    val child2 = ChannelHelper.emptyWithIdAndData(2, child2Data)

    /** CHILD 2 */
    val child3Data = ChannelData(label = "child3", adData = ChannelAdData(true))
    val child3 = ChannelHelper.emptyWithIdAndData(3, child3Data)

    object twoChildren {

      /**
        *    (0)
        *   /  \
        * (1) (2)
        *
        */
      val root = ChannelHelper.emptyWithIdAndChildren(0, children = Seq(child1, child2))
      root.updateParentRelations()

      // data node for root
      val rootData = ChannelData(label = "label", adData = ChannelAdData(true))
      root.data = rootData
    }

    object threeChildren {

      /**
        *       (0)
        *    /   |   \
        * (1)   (2)   (3)*
        *
        */
      val root = ChannelHelper.emptyWithIdAndChildren(0, children = Seq(child1, child2, child3))
      root.updateParentRelations()

      // data node for root
      val rootData = ChannelData(label = "label", adData = ChannelAdData(true))
      root.data = rootData
    }

    object movedChild {

      val dataForCopyOf3 = child3.data.copy(label = "copyOf3-label")
      val copyOf3 = child3.copy(data = dataForCopyOf3)
      val copyOf2 = child2.copy(children = Seq(copyOf3))

      /**
        *    (0)
        *   /  \
        * (1)  (2)
        *       |
        *      (3)*
        */
      val root = ChannelHelper.emptyWithIdAndChildren(0, children = Seq(child1, copyOf2))
      root.updateParentRelations()

      // data node for root
      val rootData = ChannelData(label = "label", adData = ChannelAdData(true))
      root.data = rootData
    }

  }

  "Channel" must {

    "support additions" must {

      "detect addition of new channels" in new Fixture {
        val update = twoChildren.root.diff(threeChildren.root)
        update must be(ChannelUpdate(
          added = Seq(child3),
          deleted = Seq.empty,
          moved = Seq.empty))
      }

      "apply additions to channel tree" in new Fixture {
        val root = twoChildren.root
        root.merge(threeChildren.root)

        root.children must have size 3
      }

      "maintain the data for all the nodes" in new Fixture {

        val root = twoChildren.root
        root.merge(threeChildren.root)

        root.data must be(twoChildren.rootData)
        root.findByEce(1).map(_.data) must ===(Some(child1Data))
        root.findByEce(2).map(_.data) must ===(Some(child2Data))
        root.findByEce(3).map(_.data) must ===(Some(child3Data))
      }
    }

    "support deletions" should {
      "detect deletion of channels" in new Fixture {
        private val update = threeChildren.root.diff(twoChildren.root)

        update must be(ChannelUpdate(
          added = Seq.empty,
          deleted = Seq(child3),
          moved = Seq.empty))
      }

      "apply deletions to the tree" in new Fixture {
        val root = threeChildren.root
        root.merge(twoChildren.root)

        root.children must have size 2
        root.findByEce(3) must === (None)
      }

      "maintain the data for all the nodes" in new Fixture {
        val root = threeChildren.root
        root.merge(twoChildren.root)

        root.data must be(threeChildren.rootData)
        root.findByEce(1).map(_.data) must ===(Some(child1Data))
        root.findByEce(2).map(_.data) must ===(Some(child2Data))
      }
    }
    "support moving of channels" should {
      "detect moved channels" in new Fixture {
        private val update = threeChildren.root.diff(movedChild.root)

        update must be(ChannelUpdate(
          added = Seq.empty,
          deleted = Seq.empty,
          moved = Seq(movedChild.copyOf3)
        ))
      }

      "apply movings to the tree" in new Fixture {
        val root = threeChildren.root
        root.merge(movedChild.root)

        root.children must have size 2
        root.children must not contain child3
        root.findByEce(2).map(_.children).getOrElse(Nil) must contain (movedChild.copyOf3)
        root.findByEce(3) must === (Some(child3))
      }

      "maintain the data for all the nodes" in new Fixture {

        val root = threeChildren.root
        root.merge(movedChild.root)

        root.data must be(threeChildren.rootData)
        root.findByEce(1).map(_.data) must ===(Some(child1Data))
        root.findByEce(2).map(_.data) must ===(Some(child2Data))
        // the data from the other tree (movedChild.root) must not be copied!
        root.findByEce(3).map(_.data) must ===(Some(child3Data))
      }
    }

    "produce no changes" must {
      "for twoChildren example" in new Fixture {

        private val root = twoChildren.root
        root.merge(root)
        root must be (new Fixture {}.twoChildren.root )
      }
      "for threeChildren example" in new Fixture {

        private val root = threeChildren.root
        root.merge(root)
        root must be (new Fixture {}.threeChildren.root )
      }
      "for movedChild example" in new Fixture {

        private val root = movedChild.root
        root.merge(root)
        root must be (new Fixture {}.movedChild.root )
      }
    }
  }
}
