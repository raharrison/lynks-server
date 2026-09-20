package lynks

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import lynks.comment.CommentService
import lynks.comment.comment
import lynks.common.*
import lynks.common.endpoint.health
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.common.exception.SuggestionUnavailableException
import lynks.common.inject.ServiceProvider
import lynks.db.DatabaseFactory
import lynks.entry.*
import lynks.entry.ref.EntryRefService
import lynks.group.*
import lynks.notify.NotifyService
import lynks.notify.notify
import lynks.notify.pushover.PushoverClient
import lynks.reminder.ReminderService
import lynks.reminder.reminder
import lynks.resource.*
import lynks.suggest.SuggestionService
import lynks.suggest.suggest
import lynks.task.TaskService
import lynks.task.task
import lynks.task.youtube.YoutubeDlRunner
import lynks.user.*
import lynks.util.JsonMapper.defaultMapper
import lynks.util.RandomUtils
import lynks.util.markdown.MarkdownProcessor
import lynks.worker.WorkerRegistry

fun Application.module() {
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
    }
    install(XForwardedHeaders)
    install(PartialContent)
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(defaultMapper))
    }
    install(WebSockets)
    install(CallId) {
        generate { RandomUtils.generateUuid64() }
        verify { true }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) {
        callIdMdc(MDC_REQUEST_ID)
    }
    install(StatusPages) {
        exception<InvalidModelException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Not found"))
        }
        exception<SuggestionUnavailableException> { call, cause ->
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(cause.message ?: "Suggestion unavailable"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception on ${call.request.local.method.value} ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }

    if (Environment.auth.enabled) {
        installAuth()
    }

    DatabaseFactory().connectAndMigrate()

    val workerRegistry = WorkerRegistry()
    val serviceProvider = ServiceProvider().apply {
        register(workerRegistry)
        register(WebResourceRetriever())
        register(TwoFactorService())
        register(UserService(get()))
        register(PushoverClient(get()))
        register(NotifyService(get()))
        register(FileStore())
        register(ResourceRepository())
        register(ResourceManager(get(), get()))
        register(TagService())
        register(CollectionService())
        register(GroupSetService(get(), get()))
        register(EntryAuditService())
        register(EntryRefService())
        register(EntryService(get(), get(), get()))
        register(MarkdownProcessor(get(), get()))
        register(LinkService(get(), get(), get(), get()))
        register(NoteService(get(), get(), get(), get(), get()))
        register(SnippetService(get(), get(), get(), get(), get()))
        register(FileService(get(), get(), get()))
        register(CommentService(get(), get()))
        register(ReminderService(get()))
        register(SuggestionService(get()))
        register(YoutubeDlRunner(get(), get(), get(), get()))
        register(TaskService(get(), this, get()))
        workerRegistry.init(this)
        seal()
    }

    routing {
        val prefix = Environment.server.rootPath
        route(prefix) {
            unprotectedRoutes(serviceProvider)
            if (Environment.auth.enabled) {
                authenticate("auth_session") {
                    protectedRoutes(serviceProvider)
                }
            } else {
                protectedRoutes(serviceProvider)
            }
        }
    }
}

private fun Route.protectedRoutes(serviceProvider: ServiceProvider) {
    with(serviceProvider) {
        comment(get())
        link(get())
        note(get())
        snippet(get())
        file(get())
        entry(get(), get(), get(), get())
        tag(get())
        suggest(get())
        resource(get())
        task(get())
        collection(get())
        notify(get())
        reminder(get())
        userProtected(get())
        twoFactor(get())
    }
}

private fun Route.unprotectedRoutes(serviceProvider: ServiceProvider) {
    with(serviceProvider) {
        health()
        userUnprotected(get())
    }
}

private fun Application.installAuth() {
    install(Sessions) {
        cookie<UserSession>("lynks_session", SessionStorageMemory()) {
            serializer = object : SessionSerializer<UserSession> {
                override fun serialize(session: UserSession): String {
                    return defaultMapper.writeValueAsString(session)
                }
                override fun deserialize(text: String): UserSession {
                    return defaultMapper.readValue(text)
                }
            }
            cookie.path = "/"
            cookie.secure = Environment.mode == ConfigMode.PROD
            if (Environment.auth.signingKey == null) {
                throw IllegalArgumentException("Must provide a signing key in properties when auth is enabled")
            }
            if (Environment.auth.encryptionKey == null) {
                throw IllegalArgumentException("Must provide a separate encryption key in properties when auth is enabled")
            }
            val secretSignKey = Environment.auth.signingKey
            val encryptKey = Environment.auth.encryptionKey
            transform(SessionTransportTransformerEncrypt(encryptKey.toByteArray(), secretSignKey.toByteArray()))
        }
    }

    install(Authentication) {
        session<UserSession>("auth_session") {
            validate { session -> session }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            }
        }
    }
}

fun main() {
    embeddedServer(Netty, configure = {
        connectors.add(EngineConnectorBuilder().apply {
            host = "0.0.0.0"
            port = Environment.server.port
        })
        responseWriteTimeoutSeconds = 120
    }, module = Application::module).start(true)
}
