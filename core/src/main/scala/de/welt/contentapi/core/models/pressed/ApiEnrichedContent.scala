package de.welt.contentapi.core.models.pressed

import de.welt.contentapi.core.models.content.ApiContent

/**
  * @param content       a single content. (Frank)
  * @param related       related content. (Frank)
  * @param channel       channel with breadcrumb of the content. (ConfigMcConfigFace)
  * @param configuration configuration for the content page. (ConfigMcConfigFace)
  */
case class ApiEnrichedContent(content: Option[ApiContent] = None,
                              related: Option[Seq[ApiContent]] = None,
                              channel: Option[ApiChannel] = None,
                              configuration: Option[ApiConfiguration] = None)
