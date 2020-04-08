package retriever

import common.ServerTest
import common.exception.ExecutionException
import io.restassured.RestAssured
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import resource.WebResourceRetriever
import util.JsonMapper
import util.Result

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

    @Test
    fun testGetStringResult() = runBlocking {
        val res = retriever.getStringResult("$baseUrl/health")
        if(res is Result.Success) {
            assertThat(res.value).isEqualTo(expected)
        } else {
            fail("Result is not Success")
        }
        Unit
    }

    @Test
    fun testGetStringResultFailure() = runBlocking {
        val res = retriever.getStringResult("$baseUrl/invalid")
        if(res is Result.Failure) {
            assertThat(res.reason).isInstanceOf(ExecutionException::class.java)
        } else {
            fail("Result is not Failure")
        }
        Unit
    }

    @Test
    fun testGetFileResult() = runBlocking {
        val res = retriever.getFileResult("$baseUrl/health")
        if(res is Result.Success) {
            assertThat(res.value).isEqualTo(expected.toByteArray())
        } else {
            fail("Result is not Success")
        }
        Unit
    }

    @Test
    fun testGetFileResultFailure() = runBlocking {
        val res = retriever.getStringResult("$baseUrl/invalid")
        if(res is Result.Failure) {
            assertThat(res.reason).isInstanceOf(ExecutionException::class.java)
        } else {
            fail("Result is not Failure")
        }
        Unit
    }

}