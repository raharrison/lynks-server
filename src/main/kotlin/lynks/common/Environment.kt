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
    }

    data class Resource(
        val resourceBasePath: String = config[ResourceSpec.resourceBasePath],
        val resourceTempPath: String = config[ResourceSpec.resourceTempPath],
        val binaryBasePath: String = config[ResourceSpec.binaryBasePath]
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
    }

    data class External(
        val smmryApikey: String? = config[ExternalSpec.smmryApiKey],
        val youtubeDlHost: String = config[ExternalSpec.youtubeDlHost],
        val scraperHost: String? = config[ExternalSpec.scraperHost]
    )

    val mode: ConfigMode = ConfigMode.valueOf(System.getProperty("CONFIG_MODE")?.uppercase() ?: "DEV")

    init {
        log.info("Using config mode: $mode")
    }

    private val config = Config {
        addSpec(ServerSpec)
        addSpec(DatabaseSpec)
        addSpec(ResourceSpec)
        addSpec(MailSpec)
        addSpec(ExternalSpec)
    }
        .from.json.resource("default.json")
        .from.json.resource("${mode.toString().lowercase()}.json")
        .from.json.file("config/lynks.config.json", optional = true)
        .from.json.file(System.getProperty("CONFIG_FILE") ?: "lynks.config.json", optional = true)
        .from.env()
        .from.systemProperties()

    val server = Server()
    val database = Database()
    val resource = Resource()
    val mail = Mail()
    val external = External()
}
