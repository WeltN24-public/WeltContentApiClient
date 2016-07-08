package de.welt.contentapi.client.services.contentapi

import javax.inject.{Inject, Singleton}

import de.welt.contentapi.core.models.SdpSectionData
import de.welt.contentapi.core.models.api.ApiContent
import de.welt.contentapi.core.traits.Loggable

import scala.concurrent.{ExecutionContext, Future}

case class EnrichedApiContent(content: ApiContent, sectionData: Option[SdpSectionData]) {
  def `type`: String = content.`type`
}

case class EnrichedApiResponse(main: EnrichedApiContent, related: List[EnrichedApiContent] = Nil) {
  def onward = related.filter(_.content.unwrappedRoles.contains("onward"))
  def playlist = related.filter(_.content.unwrappedRoles.contains("playlist"))
}

trait ContentEnrichmentService {
  def find(id: String)(implicit executionContext: ExecutionContext): Future[EnrichedApiResponse]
}

@Singleton
class ContentEnrichmentServiceImpl @Inject()(contentService: ContentService, sectionMetadataService: LegacySectionService)
  extends ContentEnrichmentService with Loggable {

  override def find(id: String)(implicit executionContext: ExecutionContext): Future[EnrichedApiResponse] =
    contentService.find(id).map { response ⇒
      EnrichedApiResponse(sectionMetadataService.enrich(response))
    }
}
