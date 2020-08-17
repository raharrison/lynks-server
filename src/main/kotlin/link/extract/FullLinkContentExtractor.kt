package link.extract

import com.chimbori.crux.articles.ArticleExtractor
import resource.ResourceManager
import util.*
import java.nio.file.Files

class FullLinkContentExtractor(private val resourceManager: ResourceManager = ResourceManager()) :
    LinkContentExtractor {

    private val log = loggerFor<LinkContentExtractor>()

    override fun extractContent(url: String, html: String): LinkContent {
        val extractorContent = runArticleExtract(url, html)
        return try {
            val readabilityContent = runReadability(url, html)
            LinkContent(
                readabilityContent.title,
                readabilityContent.rawContent,
                readabilityContent.extractedContent,
                extractorContent.imageUrl,
                extractorContent.keywords
            )
        } catch (e: Exception) {
            log.error("Readability call failed, falling back to article extraction result only", e)
            extractorContent
        }
    }

    private fun runReadability(url: String, html: String): LinkContent {
        resourceManager.createTempFile(url, "py").use { runnerFile ->

            resourceManager.createTempFile(url, "html").use { outputFile ->
                val params = mapOf(
                    "url" to url,
                    "content" to html,
                    "output" to outputFile.path.toAbsolutePath().toUrlString()
                )

                val template = ResourceTemplater("readability_runner.py")
                val script = template.apply(params)
                Files.writeString(runnerFile.path, script)

                val command = "python -B ${runnerFile.path.toAbsolutePath()}"
                val executeResult = ExecUtils.executeCommand(command)
                if (executeResult is Result.Failure) {
                    throw executeResult.reason
                }

                val output = Files.readString(outputFile.path)

                // first line is title, remaining is html content
                val firstLine = output.indexOf('\n')
                val title = output.substring(0, firstLine)
                val content = output.substring(firstLine + 1)
                return LinkContent(title.trim(), html, content.trim())
            }

        }

    }

    private fun runArticleExtract(url: String, html: String): LinkContent {
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