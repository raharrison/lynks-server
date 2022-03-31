package lynks

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import lynks.comment.CommentService
import lynks.comment.comment
import lynks.common.ConfigMode
import lynks.common.Environment
import lynks.common.endpoint.health
import lynks.common.exception.InvalidModelException
import lynks.common.inject.ServiceProvider
import lynks.db.DatabaseFactory
import lynks.entry.*
import lynks.group.*
import lynks.notify.NotifyService
import lynks.notify.notify
import lynks.notify.pushover.PushoverClient
import lynks.reminder.ReminderService
import lynks.reminder.reminder
import lynks.resource.ResourceManager
import lynks.resource.WebResourceRetriever
import lynks.resource.resources
import lynks.suggest.SuggestionService
import lynks.suggest.suggest
import lynks.task.TaskService
import lynks.task.task
import lynks.task.youtube.YoutubeDlRunner
import lynks.user.UserService
import lynks.user.user
import lynks.util.JsonMapper.defaultMapper
import lynks.worker.WorkerRegistry

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

    when (Environment.mode) {
        ConfigMode.TEST -> installTestFeatures()
        ConfigMode.DEV -> installDevFeatures()
        ConfigMode.PROD -> installProdFeatures()
    }

    DatabaseFactory().connectAndMigrate()

    val workerRegistry = WorkerRegistry()
    val serviceProvider = ServiceProvider().apply {
        register(workerRegistry)
        register(WebResourceRetriever())
        register(UserService())
        register(PushoverClient(get()))
        register(NotifyService(get(), get()))
        register(ResourceManager())
        register(TagService())
        register(CollectionService())
        register(GroupSetService(get(), get()))
        register(EntryAuditService())
        register(EntryService(get(), get(), get()))
        register(LinkService(get(), get(), get(), get()))
        register(NoteService(get(), get(), get()))
        register(SnippetService(get(), get(), get()))
        register(CommentService(get()))
        register(ReminderService(get()))
        register(SuggestionService(get()))
        register(YoutubeDlRunner(get(), get(), get(), get()))
        register(TaskService(get(), this, get()))
        workerRegistry.init(this)
    }

    install(Routing) {
        with(serviceProvider) {
            val prefix = Environment.server.rootPath
            route(prefix) {
                comment(get())
                link(get())
                note(get())
                snippet(get())
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
    install(CallLogging)
    install(Compression) {
        gzip()
    }
    install(PartialContent)
}

fun main() {
    embeddedServer(Netty, Environment.server.port, configure = {
        responseWriteTimeoutSeconds = 30
    }, module = Application::module).start(wait = true)
}
