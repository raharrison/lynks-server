package lynks.link

import lynks.resource.ResourceManager
import lynks.resource.WebResourceRetriever

class LinkProcessorFactory(
    private val retriever: WebResourceRetriever = WebResourceRetriever(),
    private val resourceManager: ResourceManager
) {
    private val processors =
        listOf<(String) -> LinkProcessor> { url: String ->
            YoutubeLinkProcessor(url, retriever, resourceManager)
        }

    fun createProcessors(url: String): List<LinkProcessor> {
        val processors = processors.asSequence().map { it(url) }.filter { it.matches() }.toList()
        return processors.ifEmpty {
            listOf(
                DefaultLinkProcessor(
                    url,
                    retriever,
                    resourceManager
                )
            )
        }
    }
}
