package de.welt.welt.meta

import de.welt.contentapi.core.models.{Channel, ChannelData, ChannelId}

object ChannelHelper {

  def emptyWithId(id: Long) = Channel(ChannelId(path = id.toString, ece = id), ChannelData(id.toString))

  def emptyWithIdAndData(id: Long, data: ChannelData) = Channel(ChannelId(path = id.toString, ece = id), data)

  def emptyWithIdAndChildren(id: Long, children: Seq[Channel]) = Channel(ChannelId(path = id.toString, ece = id), ChannelData(id.toString), children = children)

}
