package link

import link.extract.ExtractUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExtractUtilsTest  {

    @Test
    fun testExtractEmptyString() {
        assertThat(ExtractUtils.extractTextFromHtmlDoc(null)).isNull()
    }

    @Test
    fun testExtractNotHtml() {
        val content = "some content not html"
        assertThat(ExtractUtils.extractTextFromHtmlDoc(content)).isEqualTo(content)
    }

    @Test
    fun testExtractText() {
        val html = "<html><body><p>some text content</p></html></body>"
        assertThat(ExtractUtils.extractTextFromHtmlDoc(html)).isEqualTo("some text content")
    }

}