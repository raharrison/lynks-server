import comment.CommentService
import comment.comment
import db.DatabaseFactory
import entry.*
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.jackson.JacksonConverter
import io.ktor.routing.Routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import resource.ResourceManager
import resource.files
import suggest.SuggestionService
import suggest.suggest
import tag.TagService
import tag.tag
import util.JsonMapper.defaultMapper

fun Application.module() {
    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(defaultMapper))
    }

    DatabaseFactory()

    val tagService = TagService()
    val fileService = ResourceManager()
    val entryService = EntryService(tagService)
    val linkService = LinkService(tagService, fileService)
    val noteService = NoteService(tagService)
    val commentService = CommentService()
    val suggestionService = SuggestionService(fileService)

    install(Routing) {
        link(linkService)
        note(noteService)
        entry(entryService)
        comment(commentService)
        tag(tagService)
        suggest(suggestionService)
        files(fileService)
    }
}

fun main(args: Array<String>) {
    embeddedServer(Netty, 8080, watchPaths = listOf("Main"), module = Application::module).start()
}