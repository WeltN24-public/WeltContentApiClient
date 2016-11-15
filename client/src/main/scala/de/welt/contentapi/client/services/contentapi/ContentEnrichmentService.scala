package de.welt.contentapi.client.services.contentapi

import javax.inject.{Inject, Singleton}

import de.welt.contentapi.client.services.http.RequestHeaders
import de.welt.contentapi.core.models.content2.EnrichedApiResponse
import de.welt.contentapi.core.traits.Loggable

import scala.concurrent.{ExecutionContext, Future}

trait ContentEnrichmentService {
  def find(id: String, showRelated: Boolean = true)
          (implicit requestHeaders: Option[RequestHeaders], executionContext: ExecutionContext): Future[EnrichedApiResponse]
}

@Singleton
class ContentEnrichmentServiceImpl @Inject()(contentService: ContentService, sectionService: SectionService)
  extends ContentEnrichmentService with Loggable {

  override def find(id: String, showRelated: Boolean = true)(implicit requestHeaders: Option[RequestHeaders], executionContext: ExecutionContext): Future[EnrichedApiResponse] =
    contentService.find(id, showRelated).map { response ⇒

      EnrichedApiResponse(
        sectionService.enrich(response.content),
        response.related.getOrElse(Nil).map(sectionService.enrich)
      )
    }
}
