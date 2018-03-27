package suggest

import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import link.WebpageExtractor
import model.FileType
import service.FileService

class SuggestionService(private val fileService: FileService) {

    fun processLinkAsync(url: String): Deferred<Suggestion> = async {
        WebpageExtractor(url).use {
            val thumb = it.generateThumbnail()
            val screen = it.generateScreenshot()
            val title = it.title
            val cleanUrl = it.resolvedUrl
            val thumbPath = fileService.saveTempFile(url, thumb, FileType.THUMBNAIL)
            val screenPath = fileService.saveTempFile(url, screen, FileType.SCREENSHOT)
            fileService.saveTempFile(url, it.html.toByteArray(), FileType.DOCUMENT)
            Suggestion(cleanUrl, title, thumbPath, screenPath)
        }
    }

}