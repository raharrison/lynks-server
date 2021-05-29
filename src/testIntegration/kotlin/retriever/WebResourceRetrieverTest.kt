package retriever

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import common.exception.ExecutionException
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import resource.WebResourceRetriever
import util.Result

class WebResourceRetrieverTest {

    private val retriever = WebResourceRetriever()
    private val baseUrl = "http://localhost:3893/api"

    private lateinit var wireMockServer: WireMockServer

    @BeforeEach
    fun before() {
        wireMockServer = WireMockServer(
            options()
                .port(3893)
        )
        wireMockServer.start()
    }

    @AfterEach
    fun after() {
        wireMockServer.stop()
        wireMockServer.resetAll()
    }

    @Test
    fun testInvalidUrlReturnsNull() = runBlocking {
        assertThat(retriever.getString("invalid")).isNull()
        assertThat(retriever.getFile("invalid")).isNull()
    }

    @Test
    fun testInvalidPathReturnsNull() = runBlocking {
        assertThat(retriever.getString("$baseUrl/invalid")).isNull()
        assertThat(retriever.getFile("$baseUrl/invalid")).isNull()
    }

    @Nested
    inner class GetString {

        @Test
        fun testGetStringSuccess() = runBlocking {
            val expected = "response"
            wireMockServer.stubFor(
                get("/api/stringResponse")
                    .willReturn(ok(expected))
            )
            val res = retriever.getString("$baseUrl/stringResponse")
            assertThat(res).isEqualTo(expected)
            Unit
        }

        @Test
        fun testGetStringResult() = runBlocking {
            val expected = "response"
            wireMockServer.stubFor(
                get("/api/stringResponse")
                    .willReturn(ok(expected))
            )
            val res = retriever.getStringResult("$baseUrl/stringResponse")
            if (res is Result.Success) {
                assertThat(res.value).isEqualTo(expected)
            } else {
                fail("Result is not Success")
            }
            Unit
        }

        @Test
        fun testGetStringResultFailure() = runBlocking {
            wireMockServer.stubFor(
                get("/api/failedStringResponse")
                    .willReturn(status(500))
            )
            val res = retriever.getStringResult("$baseUrl/failedStringResponse")
            if (res is Result.Failure) {
                assertThat(res.reason).isInstanceOf(ExecutionException::class.java)
            } else {
                fail("Result is not Failure")
            }
            Unit
        }
    }

    @Nested
    inner class GetFile {

        @Test
        fun testGetFile() = runBlocking {
            val expected = "response"
            wireMockServer.stubFor(
                get("/api/fileResponse")
                    .willReturn(ok(expected))
            )
            val res = retriever.getFile("$baseUrl/fileResponse")
            assertThat(res).isEqualTo(expected.toByteArray())
            Unit
        }

        @Test
        fun testGetFileResult() = runBlocking {
            val expected = "response"
            wireMockServer.stubFor(
                get("/api/fileResponse")
                    .willReturn(ok(expected))
            )
            val res = retriever.getFileResult("$baseUrl/fileResponse")
            if (res is Result.Success) {
                assertThat(res.value).isEqualTo(expected.toByteArray())
            } else {
                fail("Result is not Success")
            }
            Unit
        }

        @Test
        fun testGetFileResultFailure() = runBlocking {
            wireMockServer.stubFor(
                get("/api/failedFileResponse")
                    .willReturn(status(500))
            )
            val res = retriever.getStringResult("$baseUrl/failedFileResponse")
            if (res is Result.Failure) {
                assertThat(res.reason).isInstanceOf(ExecutionException::class.java)
            } else {
                fail("Result is not Failure")
            }
            Unit
        }
    }

    @Nested
    inner class PostString {

        @Test
        fun testPostStringResultSuccess() = runBlocking {
            val expected = "response"
            val body = mapOf("key" to "value")
            wireMockServer.stubFor(
                post("/api/postString")
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalTo("""{"key":"value"}"""))
                    .willReturn(ok(expected))
            )
            val res = retriever.postStringResult("$baseUrl/postString", body)
            if (res is Result.Success) {
                assertThat(res.value).isEqualTo(expected)
            } else {
                fail("Result is not Success")
            }
            Unit
        }

        @Test
        fun testPostStringResultFailure() = runBlocking {
            val body = """{"key": "value"}"""
            wireMockServer.stubFor(
                post("/api/postString")
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalTo(body))
                    .willReturn(status(500))
            )
            val res = retriever.postStringResult("$baseUrl/failedPostString", body)
            if (res is Result.Failure) {
                assertThat(res.reason).isInstanceOf(ExecutionException::class.java)
            } else {
                fail("Result is not Failure")
            }
            Unit
        }

    }

}
