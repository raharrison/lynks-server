package link

import link.extract.ExtractionPolicy
import resource.WebResourceRetriever

class LinkProcessorFactory(private val retriever: WebResourceRetriever = WebResourceRetriever()) {
    private val processors =
        listOf<(ExtractionPolicy, String) -> LinkProcessor> { policy: ExtractionPolicy, url: String ->
            YoutubeLinkProcessor(policy, url, retriever)
        }

    fun createProcessors(url: String, extractionPolicy: ExtractionPolicy): List<LinkProcessor> {
        val processors = processors.asSequence().map { it(extractionPolicy, url) }.filter { it.matches() }.toList()
        return if (processors.isNotEmpty()) processors else listOf(
            DefaultLinkProcessor(
                extractionPolicy,
                url,
                retriever
            )
        )
    }
}
