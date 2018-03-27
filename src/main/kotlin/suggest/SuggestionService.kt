package suggest

import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import link.WebpageExtractor
import resource.ResourceManager
import resource.ResourceType

class SuggestionService(private val resourceManager: ResourceManager) {

    fun processLinkAsync(url: String): Deferred<Suggestion> = async {
        WebpageExtractor(url).use {
            val thumb = it.generateThumbnail()
            val screen = it.generateScreenshot()
            val title = it.title
            val cleanUrl = it.resolvedUrl
            val thumbPath = resourceManager.saveTempFile(url, thumb, ResourceType.THUMBNAIL)
            val screenPath = resourceManager.saveTempFile(url, screen, ResourceType.SCREENSHOT)
            resourceManager.saveTempFile(url, it.html.toByteArray(), ResourceType.DOCUMENT)
            Suggestion(cleanUrl, title, thumbPath, screenPath)
        }
    }

}