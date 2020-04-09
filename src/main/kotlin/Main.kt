import comment.CommentService
import comment.comment
import common.ConfigMode
import common.Environment
import common.endpoint.health
import common.exception.InvalidModelException
import common.inject.ServiceProvider
import db.DatabaseFactory
import entry.*
import group.*
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.*
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
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
import reminder.ReminderService
import reminder.reminder
import resource.ResourceManager
import resource.WebResourceRetriever
import resource.resources
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
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(defaultMapper))
    }
    install(WebSockets)
    install(StatusPages) {
        exception<InvalidModelException> {
            call.respond(HttpStatusCode.BadRequest, it.message ?: "Bad Request Format")
        }
    }

    when(Environment.mode) {
        ConfigMode.TEST -> installTestFeatures()
        ConfigMode.DEV -> installDevFeatures()
        ConfigMode.PROD -> installProdFeatures()
    }

    DatabaseFactory().connect()

    val workerRegistry = WorkerRegistry()
    val serviceProvider = ServiceProvider().apply {
        register(workerRegistry)
        register(WebResourceRetriever())
        register(UserService())
        register(ResourceManager())
        register(TagService())
        register(CollectionService())
        register(GroupSetService(get(), get()))
        register(EntryAuditService())
        register(EntryService(get(), get(), get()))
        register(LinkService(get(), get(), get(), get()))
        register(NoteService(get(), get(), get()))
        register(CommentService())
        register(ReminderService(get()))
        register(SuggestionService(get()))
        register(TaskService(get(), this, get()))
        register(NotifyService(get()))
        workerRegistry.init(this)
    }

    install(Routing) {
        with(serviceProvider) {
            val prefix = Environment.server.rootPath
            route(prefix) {
                comment(get())
                link(get())
                note(get())
                entry(get(), get(), get())
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

fun Application.installTestFeatures() {
    install(CallLogging)
}

fun Application.installDevFeatures() {
    install(CallLogging)
    install(CORS) {
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        anyHost()
        allowNonSimpleContentTypes = true
    }
}

fun Application.installProdFeatures() {
    install(Compression) {
        gzip()
    }
    install(PartialContent)
}

fun main() {
    embeddedServer(Netty, Environment.server.port, module = Application::module).start(wait = true)
}