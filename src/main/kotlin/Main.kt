import comment.CommentService
import comment.comment
import common.Environment
import common.endpoint.health
import common.inject.ServiceProvider
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
import io.ktor.websocket.WebSockets
import notify.NotifyService
import notify.notify
import resource.ResourceManager
import resource.resources
import schedule.ScheduleService
import suggest.SuggestionService
import suggest.suggest
import tag.TagService
import tag.tag
import task.TaskService
import task.task
import util.JsonMapper.defaultMapper
import worker.WorkerRegistry

fun Application.module() {
    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(defaultMapper))
    }
    install(WebSockets)

    DatabaseFactory().connect()

    val workerRegistry = WorkerRegistry()
    val serviceProvider = ServiceProvider().apply {
        register(ResourceManager())
        register(workerRegistry)
        register(TagService())
        register(EntryService(get()))
        register(LinkService(get(), get(), get()))
        register(NoteService(get(), get()))
        register(CommentService())
        register(ScheduleService())
        register(SuggestionService(get()))
        register(TaskService(get(), this, get()))
        register(NotifyService())
        workerRegistry.init(this)
    }

    install(Routing) {
        with(serviceProvider) {
            comment(get())
            link(get())
            note(get())
            entry(get())
            tag(get())
            suggest(get())
            resources(get())
            task(get())
            notify(get())
            health()
        }
    }
}

fun main(args: Array<String>) {
    embeddedServer(Netty, Environment.port, watchPaths = listOf("MainKt.main"), module = Application::module).start()
}