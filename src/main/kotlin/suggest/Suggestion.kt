package suggest

data class Suggestion(
        val url: String,
        val title: String? = null,
        val thumbnail: String? = null,
        val screenshot: String? = null
// other
)

// https://github.com/chimbori/crux