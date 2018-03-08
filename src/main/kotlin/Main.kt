import com.fasterxml.jackson.databind.SerializationFeature
import db.DatabaseFactory
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.jackson.jackson
import io.ktor.routing.Routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import service.CommentService
import service.SuggestionService
import service.TagService
import web.comment
import web.suggest
import web.tag

fun Application.module() {
    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        jackson {
            configure(SerializationFeature.INDENT_OUTPUT, true)
        }
    }

    DatabaseFactory()

    val commentService = CommentService()
    val tagService = TagService()
    val suggestionService = SuggestionService()

    install(Routing) {
        comment(commentService)
        tag(tagService)
        suggest(suggestionService)
    }
}

fun main(args: Array<String>) {
    embeddedServer(Netty, 8080, watchPaths = listOf("Main"), module = Application::module).start()
}