package de.welt.contentapi.core.models.content

import play.api.libs.json._

  case class ApiResponse(content: ApiContent,
                         related: Option[List[ApiContent]] = None) {
    private[this] def relatedFilteredBy(`type`: String): List[ApiContent] = unwrappedRelated.filter(_.unwrappedRoles.contains(`type`))

    def unwrappedRelated: List[ApiContent] = related.getOrElse(Nil)

    def relatedContent = relatedFilteredBy("related")

    def relatedPlaylist = relatedFilteredBy("playlist")
  }

  /* id field is a workaround for video playlist and related items */
  case class ApiContent(webUrl: String,
                        `type`: String,
                        subType: Option[String] = None,
                        fields: Option[Map[String, String]] = None,
                        authors: Option[List[ApiAuthor]] = None,
                        elements: Option[List[ApiElement]] = None,
                        roles: Option[List[String]] = None,
                        id: Option[String] = None,
                        sections: Option[ApiSectionData] = None,
                        tags: Option[List[ApiTag]] = None,
                        onward: Option[List[ApiOnward]] = None) {

    def unwrappedAuthors = authors.getOrElse(Nil)

    def unwrappedTags = tags.getOrElse(Nil)

    def unwrappedFields = fields.getOrElse(Map.empty)

    def unwrappedElements = elements.getOrElse(Nil)

    def unwrappedRoles = roles.getOrElse(Nil)

    def fieldsContainEntry(entry: (String, String)) = unwrappedFields.exists(_ == entry)

    def getMandatoryFieldEntry(key: String) = unwrappedFields
      .getOrElse(key, throw new IllegalStateException(s"Mandatory field '$key' not found. Content is not valid. Check PACT or BACKEND."))

    def unwrappedOnward = onward.getOrElse(List.empty)

  }

  object ApiContent {
    def emptyTextArticle: ApiContent = ApiContent(
      webUrl = "",
      `type` = "article",
      subType = None,
      id = None,
      fields = None,
      authors = None,
      elements = None
    )
  }

  case class ApiOnward(id: String, roles: Seq[String])

  case class ApiLists(lists: Option[List[ApiSection]] = None) {
    def unwrappedLists = lists.getOrElse(Nil)
  }

  case class ApiSection(id: String,
                        label: Option[String] = None,
                        content: Option[List[ApiContent]] = None) {
    def unwrappedContent = content.getOrElse(Nil)
  }

  case class ApiAuthor(name: Option[String] = None,
                       id: Option[String] = None,
                       position: Option[String] = None,
                       url: Option[String] = None,
                       elements: Option[List[ApiElement]] = None) {
    def unwrappedElements = elements.getOrElse(Nil)
  }

  case class ApiHomeSection(id: Option[String] = None,
                            parentId: Option[String] = None,
                            name: Option[String] = None,
                            displayName: Option[String] = None)

  case class ApiElement(id: String,
                        `type`: String,
                        relations: Option[List[String]] = None,
                        assets: Option[List[ApiAsset]]) {
    def unwrappedRelations = relations.getOrElse(Nil)

    def unwrappedAssets = assets.getOrElse(Nil)

    def metadataAsset = unwrappedAssets.find(_.`type` == "metadata")
  }

  case class ApiMetadata(validToDate: String) {
    def asMap: Map[String, String] = Map(
      "validToDate" -> validToDate
    )
  }

  case class ApiAsset(`type`: String,
                      fields: Option[Map[String, String]] = None,
                      metadata: Option[ApiMetadata] = None,
                      index: Option[Int] = None) {
    def unwrappedFields = fields.getOrElse(Map.empty)

    def unwrappedMetadata: Map[String, String] = metadata.map(_.asMap).getOrElse(Map.empty)
  }

  case class ApiSectionData(home: Option[String], all: Option[List[String]] = None)

  case class ApiTag(id: Option[String], value: Option[String])

  case class ApiContentSearch(path: Option[String] = None,
                              excludePaths: Option[String] = None,
                              typ: Option[String] = None,
                              subType: Option[String] = None,
                              flag: Option[String] = None,
                              limit: Option[Int] = None) {}


  /**
    * Import these into your scope to easily transform Json to the required object
    *
    * see https://www.playframework.com/documentation/2.3.x/ScalaJson
    *
    * INFO: the order of the reads is very important.
    * see: http://stackoverflow.com/q/26086815/
    */

object formats {

  implicit lazy val apiOnwardFormat = Json.format[ApiOnward]
  implicit lazy val apiMetadataFormat = Json.format[ApiMetadata]
  implicit lazy val apiAssetFormat = Json.format[ApiAsset]
  implicit lazy val apiSectionDataFormat = Json.format[ApiSectionData]
  implicit lazy val apiElementFormat = Json.format[ApiElement]
  implicit lazy val apiAuthorFormat = Json.format[ApiAuthor]
  implicit lazy val apiHomeSectionFormat = Json.format[ApiHomeSection]
  implicit lazy val apiTagFormat = Json.format[ApiTag]
  implicit lazy val apiContentFormat = Json.format[ApiContent]
  implicit lazy val apiSectionFormat = Json.format[ApiSection]
  implicit lazy val apiListsFormat = Json.format[ApiLists]
  implicit lazy val apiResponseFormat = Json.format[ApiResponse]
}