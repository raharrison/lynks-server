package lynks.link.summary

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import lynks.common.Environment
import lynks.common.exception.ExecutionException
import lynks.resource.ResourceRetriever
import lynks.util.JsonMapper
import lynks.util.Result
import lynks.util.loggerFor

class LinkSummarizer(private val resourceRetriever: ResourceRetriever) {

    private val log = loggerFor<LinkSummarizer>()

    private data class SmmryResponse(
        @JsonProperty("sm_api_keyword_array") val keywords: List<String>?,
        @JsonProperty("sm_api_character_count") val characterCount: Int?,
        @JsonProperty("sm_api_content_reduced") val reduced: String?,
        @JsonProperty("sm_api_title") val title: String?,
        @JsonProperty("sm_api_content") val content: String?,
        @JsonProperty("sm_api_error") val errorCode: Int?,
        @JsonProperty("sm_api_limitation") val limitation: String?,
        @JsonProperty("sm_api_message") val message: String?
    )

    suspend fun generateSummary(target: String): Result<Summary, ExecutionException> {
        val key = Environment.external.smmryApiKey ?: return Result.Failure(ExecutionException("No Smmry API key found"))

        val reqUrl = "http://api.smmry.com/&SM_API_KEY=$key&SM_KEYWORD_COUNT=5&SM_WITH_BREAK&SM_IGNORE_LENGTH&SM_URL=$target"

        when (val response = resourceRetriever.getStringResult(reqUrl)) {
            is Result.Success -> {
                val smmryResponse = JsonMapper.defaultMapper.readValue<SmmryResponse>(response.value)
                if (smmryResponse.errorCode != null) {
                    log.error("Smmry api call returned error code ${smmryResponse.errorCode}: ${smmryResponse.message}")
                    return Result.Failure(
                        ExecutionException(
                            smmryResponse.message ?: "Could not generate summary",
                            smmryResponse.errorCode
                        )
                    )
                }
                return Result.Success(mapToSummary(smmryResponse))
            }
            is Result.Failure -> {
                log.error("Link smmry api call failed", response.reason)
                return Result.Failure(response.reason)
            }
        }
    }

    private fun mapToSummary(response: SmmryResponse): Summary {
        val content = response.content!!.split("[BREAK]").joinToString("") { "<p>$it</p>" }
        return Summary(
            response.title!!,
            content,
            response.keywords!!,
            response.reduced!!
        )
    }
}
