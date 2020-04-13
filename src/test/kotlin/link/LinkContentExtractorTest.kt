package link

import common.exception.ExecutionException
import io.mockk.*
import link.extract.LinkContentExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import resource.ResourceManager
import util.ExecUtils
import util.Result
import java.nio.file.Files

class LinkContentExtractorTest  {

    private val resourceManager = spyk<ResourceManager>()
    private val contentExtractor = LinkContentExtractor(resourceManager)

    private val url = "https://ryanharrison.co.uk/2020/04/12/kotlin-java-ci-with-github-actions.html"
    private val rawHtml = this.javaClass.getResource("/content_extract_raw.html").readText()
    private val readabilityOutput = this.javaClass.getResource("/readability_output.html").readText()

    @Test
    fun testExtractContentSucceeds() {
        mockkObject(ExecUtils)

        val outputFile = resourceManager.createTempFile(url, "html")
        Files.writeString(outputFile.path, readabilityOutput)
        every { resourceManager.createTempFile(url, "html") } returns outputFile
        every { ExecUtils.executeCommand(any()) } returns Result.Success(readabilityOutput)

        val linkContent = contentExtractor.extractContent(url, rawHtml)

        verify(exactly = 1) {
            ExecUtils.executeCommand(match {
                it.startsWith("python")
            })
        }

        unmockkObject(ExecUtils)

        val title = "Kotlin & Java CI with Github Actions"
        assertThat(linkContent.title).isEqualTo(title)
        assertThat(linkContent.imageUrl).isNotNull()
        assertThat(linkContent.keywords).hasSize(7).contains("kotlin", "github", "actions", "build", "java")
        assertThat(linkContent.content).doesNotContain("<html>").doesNotContain("<script>").doesNotStartWith(title)
    }

    @Test
    fun testExtractReadabilityFailsFallback() {
        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Failure(ExecutionException("error"))

        val linkContent = contentExtractor.extractContent(url, rawHtml)

        verify(exactly = 1) {
            ExecUtils.executeCommand(match {
                it.startsWith("python")
            })
        }

        unmockkObject(ExecUtils)

        val title = "Kotlin & Java CI with Github Actions - Ryan Harrison"
        assertThat(linkContent.title).isEqualTo(title)
        assertThat(linkContent.imageUrl).isNotNull()
        assertThat(linkContent.keywords).hasSize(7).contains("kotlin", "github", "actions", "build", "java")
        assertThat(linkContent.content).doesNotContain("<html>").doesNotContain("<script>").doesNotStartWith(title)
    }

}