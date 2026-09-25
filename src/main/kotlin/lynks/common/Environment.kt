package lynks.common

import com.voltstorage.konf.Config
import com.voltstorage.konf.ConfigSpec
import lynks.util.loggerFor

enum class ConfigMode {
    DEV, TEST, PROD
}

private val log = loggerFor<Environment>()

object Environment {

    private object ServerSpec : ConfigSpec("server") {
        val host by optional("127.0.0.1", description = "address the server will bind to")
        val port by optional(8080, description = "port the server will bind to")
        val rootPath by required<String>(description = "root path of all routes")
    }

    data class Server(
        val host: String = config[ServerSpec.host],
        val port: Int = config[ServerSpec.port],
        val rootPath: String = config[ServerSpec.rootPath]
    )

    private object AuthSpec : ConfigSpec("auth") {
        val enabled by required<Boolean>(description = "protect all endpoints to be accessible only to authorized users")
        val passwordLoginEnabled by optional(
            default = true,
            description = "if users can sign in with their username and password, the fallback when single sign-on is unavailable"
        )
        val defaultUserName by optional(
            "user",
            description = "user created on first start, which every request acts as when auth is disabled"
        )
        val defaultUserPassword by optional<String?>(null, description = "password raw text or bcrypt hash for the default user")
    }

    private object SessionSpec : ConfigSpec("auth.session") {
        val idleDays by optional(30, description = "days of inactivity after which a session expires")
        val maxDays by optional(90, description = "days after sign in when a session expires however active it is")
    }

    private object OidcSpec : ConfigSpec("auth.oidc") {
        val enabled by optional(false, description = "offer single sign-on through an OpenID Connect provider")
        val issuer by optional<String?>(null, description = "issuer url of the provider, e.g. https://auth.example.com")
        val clientId by optional<String?>(null, description = "client id registered with the provider")
        val clientSecret by optional<String?>(
            null,
            description = "client secret registered with the provider, should be kept secret"
        )
        val redirectUri by optional<String?>(
            null,
            description = "callback registered with the provider, e.g. https://lynks.example.com/api/auth/oidc/callback"
        )
        val label by optional("Sign in with SSO", description = "text of the single sign-on button")
    }

    data class Auth(
        val enabled: Boolean = config[AuthSpec.enabled],
        val passwordLoginEnabled: Boolean = config[AuthSpec.passwordLoginEnabled],
        val defaultUserName: String = config[AuthSpec.defaultUserName],
        val defaultUserPassword: String? = config[AuthSpec.defaultUserPassword],
        val session: AuthSession = AuthSession(),
        val oidc: Oidc = Oidc()
    ) {
        fun validate() {
            require(passwordLoginEnabled || oidc.enabled) {
                "auth.passwordLoginEnabled and auth.oidc.enabled are both false, so nobody could sign in"
            }
            require(session.idleDays > 0 && session.maxDays >= session.idleDays) {
                "auth.session.idleDays must be positive and no more than auth.session.maxDays"
            }
            if (oidc.enabled) {
                require(enabled) { "auth.oidc.enabled needs auth.enabled" }
                require(isHttpUrl(oidc.issuer)) { "auth.oidc.issuer must be an http(s) url when auth.oidc.enabled" }
                require(!oidc.clientId.isNullOrBlank()) { "auth.oidc.clientId is required when auth.oidc.enabled" }
                require(!oidc.clientSecret.isNullOrBlank()) { "auth.oidc.clientSecret is required when auth.oidc.enabled" }
                require(isHttpUrl(oidc.redirectUri)) { "auth.oidc.redirectUri must be an http(s) url when auth.oidc.enabled" }
            }
        }
    }

    private fun isHttpUrl(value: String?): Boolean {
        val uri = value?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return false
        return uri.scheme in setOf("http", "https") && !uri.host.isNullOrEmpty()
    }

    data class AuthSession(
        val idleDays: Int = config[SessionSpec.idleDays],
        val maxDays: Int = config[SessionSpec.maxDays]
    )

    data class Oidc(
        val enabled: Boolean = config[OidcSpec.enabled],
        val issuer: String? = config[OidcSpec.issuer],
        val clientId: String? = config[OidcSpec.clientId],
        val clientSecret: String? = config[OidcSpec.clientSecret],
        val redirectUri: String? = config[OidcSpec.redirectUri],
        val label: String = config[OidcSpec.label]
    )

    private object DatabaseSpec: ConfigSpec("database") {
        val url by required<String>(description = "url of the database to connect to")
        val user by optional("", description = "database user")
        val password by optional("", description = "database password")
    }

    data class Database(
        val url: String = config[DatabaseSpec.url],
        val user: String = config[DatabaseSpec.user],
        val password: String = config[DatabaseSpec.password],
    )

    private object ResourceSpec: ConfigSpec("resource") {
        val resourceBasePath by required<String>(description = "location where all main entry resources will be saved")
        val resourceTempPath by required<String>(description = "location where all temporary files will be saved")
        val tempFileCleanInterval by optional(6, description = "frequency in hours to delete temp resources")
        val maxTempResourceAge by optional(14, description = "maximum age of temp files in days before qualifying for cleanup")
    }

    data class Resource(
        val resourceBasePath: String = config[ResourceSpec.resourceBasePath],
        val resourceTempPath: String = config[ResourceSpec.resourceTempPath],
        val tempFileCleanInterval: Int = config[ResourceSpec.tempFileCleanInterval],
        val maxTempResourceAge: Int = config[ResourceSpec.maxTempResourceAge]
    )

    private object ExternalSpec : ConfigSpec("external") {
        val youtubeApiKey by optional<String?>(null, description = "api key to YouTube Data API v3")
        val youtubeApiBaseUrl by optional("https://www.googleapis.com", description = "base url of the YouTube Data API v3")
        val scraperHost by optional<String?>(null, description = "url to the scraper component")
        val joltHost by optional<String?>(null, description = "base url of the jolt api, e.g. https://jolt.example.com/api/v1")
    }

    data class External(
        val youtubeApiKey: String? = config[ExternalSpec.youtubeApiKey],
        val youtubeApiBaseUrl: String = config[ExternalSpec.youtubeApiBaseUrl],
        val scraperHost: String? = config[ExternalSpec.scraperHost],
        val joltHost: String? = config[ExternalSpec.joltHost]
    )

    val mode: ConfigMode = ConfigMode.valueOf(System.getProperty("CONFIG_MODE")?.uppercase() ?: "DEV")

    init {
        log.info("Using config mode: $mode")
    }

    private val config = Config {
        addSpec(ServerSpec)
        addSpec(AuthSpec)
        addSpec(SessionSpec)
        addSpec(OidcSpec)
        addSpec(DatabaseSpec)
        addSpec(ResourceSpec)
        addSpec(ExternalSpec)
    }
        .from.json.resource("default.json")
        .from.json.resource("${mode.toString().lowercase()}.json")
        .from.json.file("config/lynks${if(mode == ConfigMode.TEST) "-$mode" else ""}.config.json", optional = true)
        .from.json.file(System.getProperty("CONFIG_FILE") ?: "lynks.config.json", optional = true)
        .from.env()
        .from.systemProperties()

    val server = Server()
    val auth = Auth()
    val database = Database()
    val resource = Resource()
    val external = External()
}
