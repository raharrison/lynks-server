package link.extract

interface LinkContentExtractor {

    fun extractContent(url: String, html: String): LinkContent
}
