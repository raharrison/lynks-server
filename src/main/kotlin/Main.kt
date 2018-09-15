import comment.CommentService
import comment.comment
import common.Environment
import common.endpoint.health
import common.exception.InvalidModelException
import common.inject.ServiceProvider
import db.DatabaseFactory
import entry.*
import group.CollectionService
import group.TagService
import group.collection
import group.tag
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.JacksonConverter
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.route
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import notify.NotifyService
import notify.notify
import resource.ResourceManager
import resource.resources
import schedule.ReminderService
import schedule.reminder
import suggest.SuggestionService
import suggest.suggest
import task.TaskService
import task.task
import user.UserService
import user.user
import util.JsonMapper.defaultMapper
import worker.WorkerRegistry

fun Application.module() {
    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(defaultMapper))
    }
    install(WebSockets)

    install(StatusPages) {
        exception<InvalidModelException> {
            call.respond(HttpStatusCode.BadRequest, it.message ?: "Bad Request Format")
        }
    }

    DatabaseFactory().connect()

    val workerRegistry = WorkerRegistry()
    val serviceProvider = ServiceProvider().apply {
        register(workerRegistry)
        register(UserService())
        register(ResourceManager())
        register(TagService())
        register(CollectionService())
        register(EntryService(get(), get()))
        register(LinkService(get(), get(), get(), get()))
        register(NoteService(get(), get(), get()))
        register(CommentService())
        register(ReminderService())
        register(SuggestionService(get()))
        register(TaskService(get(), this, get()))
        register(NotifyService(get()))
        workerRegistry.init(this)
    }

    install(Routing) {
        with(serviceProvider) {
            route("/api") {
                comment(get())
                link(get())
                note(get())
                entry(get(), get())
                tag(get())
                suggest(get())
                resources(get())
                task(get())
                collection(get())
                notify(get())
                health()
                reminder(get())
                user(get(), get())
            }
        }
    }
}

fun main(args: Array<String>) {
    embeddedServer(Netty, Environment.server.port, watchPaths = listOf("MainKt.main"), module = Application::module).start()
}