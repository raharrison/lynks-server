package link.extract

import com.chimbori.crux.articles.ArticleExtractor

class PartialLinkContentExtractor() : LinkContentExtractor {

    override fun extractContent(url: String, html: String): LinkContent {
        val article = ArticleExtractor.with(url, html)
            .extractMetadata()
            .extractContent()
            .article()
        return LinkContent(
            title = article.title,
            rawContent = html,
            extractedContent = article.document.toString(),
            imageUrl = article.imageUrl,
            keywords = article.keywords.toSet()
        )
    }

}
