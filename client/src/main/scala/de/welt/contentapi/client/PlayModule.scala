package de.welt.contentapi.client

import de.welt.contentapi.client.services.configuration.{SectionConfigurationService, SectionConfigurationServiceImpl}
import de.welt.contentapi.client.services.configuration.{ContentClientConfig, ContentClientConfigImpl}
import de.welt.contentapi.client.services.contentapi._
import de.welt.contentapi.client.services.s3.{S3, S3Impl}
import play.api.inject.Module
import play.api.{Configuration, Environment}

class PlayModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration) = {
    Seq(
      bind(classOf[ContentClientConfig]).to(classOf[ContentClientConfigImpl]).eagerly,
      bind(classOf[ContentService]).to(classOf[ContentServiceImpl]).eagerly,

      bind(classOf[S3]).to(classOf[S3Impl]),

      bind(classOf[ContentEnrichmentService]).to(classOf[ContentEnrichmentServiceImpl]),
      bind(classOf[SectionConfigurationService]).to(classOf[SectionConfigurationServiceImpl]),
      bind(classOf[LegacySectionService]).to(classOf[LegacySectionServiceImpl]),
      bind(classOf[ContentSearchService]).to(classOf[ContentSearchServiceImpl])

    )
  }
}
