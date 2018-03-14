package service

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import link.WebpageExtractor
import model.FileType
import model.Suggestion

class SuggestionService(private val fileService: FileService) {

    suspend fun processLinkAsync(url: String): Suggestion = withContext(CommonPool) {
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

}