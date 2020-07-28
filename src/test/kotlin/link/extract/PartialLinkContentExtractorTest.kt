package link.extract

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PartialLinkContentExtractorTest  {

    private val contentExtractor = PartialLinkContentExtractor()

    private val url = "https://ryanharrison.co.uk/2020/04/12/kotlin-java-ci-with-github-actions.html"
    private val rawHtml = this.javaClass.getResource("/content_extract_raw.html").readText()

    @Test
    fun testExtractContentSucceeds() {

        val linkContent = contentExtractor.extractContent(url, rawHtml)

        val title = "Kotlin & Java CI with Github Actions - Ryan Harrison"
        assertThat(linkContent.title).isEqualTo(title)
        assertThat(linkContent.imageUrl).isNotNull()
        assertThat(linkContent.keywords).hasSize(7).contains("kotlin", "github", "actions", "build", "java")
        assertThat(linkContent.rawContent).isEqualTo(rawHtml)
        assertThat(linkContent.extractedContent).doesNotContain("<html>").doesNotContain("<script>")
            .doesNotStartWith(title)
    }

}
