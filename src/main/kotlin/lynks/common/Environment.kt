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
        val port by optional(8080, description = "port the server will bind to")
        val rootPath by required<String>(description = "root path of all routes")
    }

    data class Server(
        val port: Int = config[ServerSpec.port],
        val rootPath: String = config[ServerSpec.rootPath]
    )

    private object AuthSpec : ConfigSpec("auth") {
        val enabled by required<Boolean>(description = "protect all endpoints to be accessible only to authorized users")
        val registrationsEnabled by optional(default = false, description = "if new users can be registered (still requiring activation)")
        val signingKey by optional<String?>(null, description = "key (32 chars) used to sign session cookies, should be kept secret")
        val encryptionKey by optional<String?>(null, description = "key (16+ chars) used to encrypt session cookies, should be kept secret and different from signingKey")
        val defaultUserName by optional("user", description = "username for default auto-created user")
        val defaultUserPassword by optional<String?>(null, description = "password raw text or bcrypt hash for auto-created user")
    }

    data class Auth(
        val enabled: Boolean = config[AuthSpec.enabled],
        val registrationsEnabled: Boolean = config[AuthSpec.registrationsEnabled],
        val signingKey: String? = config[AuthSpec.signingKey],
        val encryptionKey: String? = config[AuthSpec.encryptionKey],
        val defaultUserName: String = config[AuthSpec.defaultUserName],
        val defaultUserPassword: String? = config[AuthSpec.defaultUserPassword]
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
        val binaryBasePath by required<String>(description = "location where all binary utilities will be saved")
        val tempFileCleanInterval by optional(6, description = "frequency in hours to delete temp resources")
        val maxTempResourceAge by optional(14, description = "maximum age of temp files in days before qualifying for cleanup")
    }

    data class Resource(
        val resourceBasePath: String = config[ResourceSpec.resourceBasePath],
        val resourceTempPath: String = config[ResourceSpec.resourceTempPath],
        val binaryBasePath: String = config[ResourceSpec.binaryBasePath],
        val tempFileCleanInterval: Int = config[ResourceSpec.tempFileCleanInterval],
        val maxTempResourceAge: Int = config[ResourceSpec.maxTempResourceAge]
    )

    private object MailSpec : ConfigSpec("mail") {
        val enabled by required<Boolean>(description = "enable sending emails")
        val server by required<String>(description = "host of mail server to use")
        val port by required<Int>(description = "port of mail server to use")
    }

    data class Mail(
        val enabled: Boolean = config[MailSpec.enabled],
        val server: String = config[MailSpec.server],
        val port: Int = config[MailSpec.port]
    )

    private object ExternalSpec : ConfigSpec("external") {
        val smmryApiKey by optional<String?>(null, description = "api key to smmry.com")
        val youtubeDlHost by required<String>(description = "url of latest youtube-dl binary")
        val scraperHost by optional<String?>(null, description = "url to the scraper component")
        val pushoverToken by optional<String?>(null, description = "Pushover application token")
        val pushoverUser by optional<String?>(null, description = "Pushover user/group key")
    }

    data class External(
        val smmryApiKey: String? = config[ExternalSpec.smmryApiKey],
        val youtubeDlHost: String = config[ExternalSpec.youtubeDlHost],
        val scraperHost: String? = config[ExternalSpec.scraperHost],
        val pushoverToken: String? = config[ExternalSpec.pushoverToken],
        val pushoverUser: String? = config[ExternalSpec.pushoverUser]
    )

    val mode: ConfigMode = ConfigMode.valueOf(System.getProperty("CONFIG_MODE")?.uppercase() ?: "DEV")

    init {
        log.info("Using config mode: $mode")
    }

    private val config = Config {
        addSpec(ServerSpec)
        addSpec(AuthSpec)
        addSpec(DatabaseSpec)
        addSpec(ResourceSpec)
        addSpec(MailSpec)
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
    val mail = Mail()
    val external = External()
}
