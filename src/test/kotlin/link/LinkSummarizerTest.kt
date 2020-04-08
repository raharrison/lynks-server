package link

import common.exception.ExecutionException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import link.summary.LinkSummarizer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import resource.ResourceRetriever
import util.Result

class LinkSummarizerTest {

    private val resourceRetriever = mockk<ResourceRetriever>()
    private val linkSummarizer = LinkSummarizer(resourceRetriever)

    @Test
    fun testGenerateLinkSummary() = runBlocking {
        val successResponse = this.javaClass.getResource("/smmry_response.json").readText()
        coEvery { resourceRetriever.getStringResult(any()) } returns Result.Success(successResponse)

        val result = linkSummarizer.generateSummary("ryaharrison.co.uk")
        if (result is Result.Success) {
            assertThat(result.value.title).isEqualTo("Proxy API Requests")
            assertThat(result.value.content).hasSizeGreaterThan(1000)
            assertThat(result.value.content).contains("<p>", "</p>")
            assertThat(result.value.reduced).endsWith("%")
            assertThat(result.value.keywords).hasSize(5)
        } else {
            fail("Response is not Success")
        }
        Unit
    }

    @Test
    fun testWebRetrievalFailsReturnsFailure() = runBlocking {
        val exception = ExecutionException("error")
        coEvery { resourceRetriever.getStringResult(any()) } returns Result.Failure(exception)

        val result = linkSummarizer.generateSummary("ryaharrison.co.uk")
        if (result is Result.Failure) {
            assertThat(result.reason).isEqualTo(exception)
        } else {
            fail("Response is not Failure")
        }
        Unit
    }

    @Test
    fun testSummaryResponseContainsErrorCode() = runBlocking {
        val errorResponse = """{"sm_api_error":3, "sm_api_message":"SOURCE IS TOO SHORT"}"""
        coEvery { resourceRetriever.getStringResult(any()) } returns Result.Success(errorResponse)

        val result = linkSummarizer.generateSummary("ryaharrison.co.uk")
        if (result is Result.Failure) {
            assertThat(result.reason.code).isEqualTo(3)
            assertThat(result.reason.message).isNotEmpty()
        } else {
            fail("Response is not Failure")
        }
        Unit
    }

}