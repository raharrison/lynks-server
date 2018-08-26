package retriever

import common.ServerTest
import io.restassured.RestAssured
import kotlinx.coroutines.experimental.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import resource.WebResourceRetriever
import util.JsonMapper

class WebResourceRetrieverTest: ServerTest() {

    private val retriever = WebResourceRetriever()
    private val expected = JsonMapper.defaultMapper.writeValueAsString(mapOf("status" to "ok"))
    private val baseUrl = "${RestAssured.baseURI}:${RestAssured.port}${RestAssured.basePath}"

    @Test
    fun testInvalidUrlReturnsNull() = runBlocking {
        assertThat(retriever.getString("invalid")).isNull()
        assertThat(retriever.getFile("invalid")).isNull()
    }

    @Test
    fun testInvalidPathReturnsNull()= runBlocking {
        assertThat(retriever.getString("$baseUrl/invalid")).isNull()
        assertThat(retriever.getFile("$baseUrl/invalid")).isNull()
    }

    @Test
    fun testGetString()= runBlocking {
        val res = retriever.getString("$baseUrl/health")
        assertThat(res).isEqualTo(expected)
        Unit
    }

    @Test
    fun testGetFile()= runBlocking {
        val res = retriever.getFile("$baseUrl/health")
        assertThat(res).isEqualTo(expected.toByteArray())
        Unit
    }

}