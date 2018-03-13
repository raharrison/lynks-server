package service

import com.chimbori.crux.articles.ArticleExtractor
import kotlinx.coroutines.experimental.async
import link.WebpageExtractor
import model.FileType
import model.Suggestion
import java.net.URL

class SuggestionService(val fileService: FileService) {

    suspend fun suggest(suggestion: Suggestion): Suggestion {
        val html = async {
            URL(suggestion.url).readText()
        }

        val article = ArticleExtractor.with(suggestion.url, html.await())
                .extractMetadata()
                .extractContent()
                .article()
        return Suggestion(suggestion.url, article.title, article.imageUrl)
    }

    suspend fun processLink(url: String): Suggestion =
            WebpageExtractor(url).use {
                val thumb = it.generateThumbnail()
                val screen = it.generateScreenshot()
                val title = it.title
                val cleanUrl = it.resolvedUrl
                val thumbPath = fileService.saveTempFile(url, thumb, FileType.THUMBNAIL)
                val screenPath = fileService.saveTempFile(url, screen, FileType.SCREENSHOT)
                Suggestion(cleanUrl, title, thumbPath, screenPath)
            }

}