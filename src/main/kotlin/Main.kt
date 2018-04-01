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
import resource.resources
import suggest.SuggestionService
import suggest.suggest
import tag.TagService
import tag.tag
import util.JsonMapper.defaultMapper
import worker.WorkerRegistry

fun Application.module() {
    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(defaultMapper))
    }

    DatabaseFactory()

    val resourceManager = ResourceManager()
    val workerRegistry = WorkerRegistry(resourceManager)

    val tagService = TagService()
    val entryService = EntryService(tagService)
    val linkService = LinkService(tagService, resourceManager, workerRegistry)
    val noteService = NoteService(tagService)
    val commentService = CommentService()
    val suggestionService = SuggestionService(workerRegistry)


    install(Routing) {
        link(linkService)
        note(noteService)
        entry(entryService)
        comment(commentService)
        tag(tagService)
        suggest(suggestionService)
        resources(resourceManager)
    }
}

fun main(args: Array<String>) {
    embeddedServer(Netty, 8080, watchPaths = listOf("Main"), module = Application::module).start()
}