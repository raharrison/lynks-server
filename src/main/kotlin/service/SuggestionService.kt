package service

import com.chimbori.crux.articles.ArticleExtractor
import kotlinx.coroutines.experimental.async
import model.Suggestion
import java.net.URL

class SuggestionService {

    suspend fun suggest(suggestion: Suggestion): Suggestion {
        val html = async {
            URL(suggestion.url).readText()
        }

        val article = ArticleExtractor.with(suggestion.url, html.await())
                .extractMetadata()
                .extractContent()
                .article()
        return Suggestion(suggestion.url, article.title, article.imageUrl)
    }

}