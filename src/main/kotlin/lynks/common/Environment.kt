package lynks.common

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import lynks.db.DatabaseDialect
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
        val signingKey by optional<String?>(null, description = "key (32 chars) used to sign and encrypt session cookies, should be kept secret")
        val defaultUserName by optional("user", description = "username for default auto-created user")
        val defaultUserPassword by optional<String?>(null, description = "password raw text or bcrypt hash for auto-created user")
    }

    data class Auth(
        val enabled: Boolean = config[AuthSpec.enabled],
        val signingKey: String? = config[AuthSpec.signingKey],
        val defaultUserName: String = config[AuthSpec.defaultUserName],
        val defaultUserPassword: String? = config[AuthSpec.defaultUserPassword]
    )

    private object DatabaseSpec: ConfigSpec("database") {
        val dialect by required<DatabaseDialect>(description = "type of database: either H2 or POSTGRES")
        val url by required<String>(description = "url of the database to connect to")
        val user by optional("", description = "database user")
        val password by optional("", description = "database password")
    }

    data class Database(
        val dialect: DatabaseDialect = config[DatabaseSpec.dialect],
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
