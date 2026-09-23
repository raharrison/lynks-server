package lynks

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
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
import lynks.comment.CommentService
import lynks.comment.comment
import lynks.common.*
import lynks.common.endpoint.health
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.common.exception.SuggestionUnavailableException
import lynks.common.exception.UnauthorizedException
import lynks.common.inject.ServiceProvider
import lynks.db.DatabaseFactory
import lynks.digest.DigestService
import lynks.digest.digest
import lynks.entry.*
import lynks.entry.ref.EntryRefService
import lynks.group.*
import lynks.notify.NotifyService
import lynks.notify.jolt.JoltClient
import lynks.notify.notify
import lynks.reminder.ReminderService
import lynks.reminder.reminder
import lynks.resource.*
import lynks.suggest.SuggestionService
import lynks.suggest.suggest
import lynks.task.TaskService
import lynks.task.task
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
    install(CallId) {
        generate { RandomUtils.generateUuid64() }
        verify { true }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) {
        callIdMdc(MDC_REQUEST_ID)
        disableDefaultColors()
    }
    install(StatusPages) {
        exception<InvalidModelException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Not found"))
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(cause.message ?: "Unauthorized"))
        }
        // malformed or mistyped request bodies from call.receive
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<SuggestionUnavailableException> { call, cause ->
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(cause.message ?: "Suggestion unavailable"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception on ${call.request.local.method.value} ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }

    DatabaseFactory().connectAndMigrate()

    val workerRegistry = WorkerRegistry()
    val serviceProvider = ServiceProvider().apply {
        register(workerRegistry)
        register(WebResourceRetriever())
        register(TwoFactorService())
        register(UserService(get()))
        register(JoltClient(get()))
        register(NotifyService(get(), get()))
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
        register(DigestService(get()))
        register(SuggestionService(get()))
        register(TaskService(get(), this, get()))
        workerRegistry.init(this)
        seal()
    }

    val userService = serviceProvider.get<UserService>()
    userService.ensureDefaultUser()
    installAuth(userService)

    routing {
        val prefix = Environment.server.rootPath
        route(prefix) {
            unprotectedRoutes(serviceProvider)
            authenticate(AUTH_PROVIDER) {
                protectedRoutes(serviceProvider)
            }
        }
    }
}

private const val AUTH_PROVIDER = "auth_session"

private fun Route.protectedRoutes(serviceProvider: ServiceProvider) {
    with(serviceProvider) {
        comment(get())
        digest(get())
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

private fun Application.installAuth(userService: UserService) {
    if (!Environment.auth.enabled) {
        install(Authentication) {
            provider(AUTH_PROVIDER) {
                authenticate { context ->
                    val principal = userService.getPrincipal(Environment.auth.defaultUserName)
                    if (principal != null) {
                        context.principal(principal)
                    } else {
                        context.challenge(AUTH_PROVIDER, AuthenticationFailedCause.NoCredentials) { challenge, call ->
                            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                            challenge.complete()
                        }
                    }
                }
            }
        }
        return
    }

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
        session<UserSession>(AUTH_PROVIDER) {
            // looked up on every request so deactivating a user ends their existing sessions
            validate { session -> userService.getPrincipal(UserId(session.userId)) }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            }
        }
    }
}

fun main() {
    embeddedServer(Netty, configure = {
        connectors.add(EngineConnectorBuilder().apply {
            host = Environment.server.host
            port = Environment.server.port
        })
        responseWriteTimeoutSeconds = 120
    }, module = Application::module).start(true)
}
