package lynks

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
import lynks.auth.*
import lynks.comment.CommentService
import lynks.comment.comment
import lynks.common.ConfigMode
import lynks.common.Environment
import lynks.common.ErrorResponse
import lynks.common.MDC_REQUEST_ID
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
import lynks.user.TwoFactorService
import lynks.user.UserService
import lynks.user.twoFactor
import lynks.user.userProtected
import lynks.util.JsonMapper.defaultMapper
import lynks.util.RandomUtils
import lynks.util.markdown.MarkdownProcessor
import lynks.worker.WorkerRegistry
import org.slf4j.event.Level

fun Application.module() {
    installPlugins()

    DatabaseFactory().connectAndMigrate()

    val authConfig = Environment.auth
    val workerRegistry = WorkerRegistry()
    val serviceProvider = ServiceProvider().apply {
        register(workerRegistry)
        register(WebResourceRetriever())
        register(TwoFactorService())
        register(UserService(get()))
        register(SessionService(authConfig.session))
        register(AuthCookies(Environment.mode == ConfigMode.PROD, get<SessionService>().maxAge))
        register(OidcService(authConfig.oidc, get()))
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
    installAuth(authConfig, userService, serviceProvider.get(), serviceProvider.get())

    routing {
        val prefix = Environment.server.rootPath
        route(prefix) {
            unprotectedRoutes(serviceProvider, authConfig)
            authenticate(AUTH_PROVIDER) {
                protectedRoutes(serviceProvider, authConfig)
            }
        }
    }
}

fun Application.installPlugins() {
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
    }
    // nginx appends the address it saw, so the last entry is the one a client cannot forge
    install(XForwardedHeaders) {
        useLastProxy()
    }
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
        level = Level.DEBUG
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
            call.application.log.error(
                "Unhandled exception on ${call.request.local.method.value} ${call.request.local.uri}",
                cause
            )
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }
}

private fun Route.protectedRoutes(serviceProvider: ServiceProvider, authConfig: Environment.Auth) {
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
        userProtected(get(), get())
        authProtected(get())
        twoFactor(get())
    }
}

private fun Route.unprotectedRoutes(serviceProvider: ServiceProvider, authConfig: Environment.Auth) {
    with(serviceProvider) {
        health()
        authUnprotected(authConfig, get(), get(), get(), get())
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
