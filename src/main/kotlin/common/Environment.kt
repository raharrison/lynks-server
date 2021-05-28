package common

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import util.loggerFor

enum class ConfigMode {
    DEV, TEST, PROD
}

private val log = loggerFor<Environment>()

object Environment {

    private object ServerSpec : ConfigSpec("server") {
        val database by required<String>()
        val driver by required<String>()
        val port by optional(8080)
        val rootPath by required<String>()
    }

    data class Server(
        val database: String = config[ServerSpec.database],
        val driver: String = config[ServerSpec.driver],
        val port: Int = config[ServerSpec.port],
        val rootPath: String = config[ServerSpec.rootPath]
    )

    private object ResourceSpec: ConfigSpec("resource") {
        val resourceBasePath by required<String>()
        val resourceTempPath by required<String>()
        val binaryBasePath by required<String>()
    }

    data class Resource(
        val resourceBasePath: String = config[ResourceSpec.resourceBasePath],
        val resourceTempPath: String = config[ResourceSpec.resourceTempPath],
        val binaryBasePath: String = config[ResourceSpec.binaryBasePath]
    )

    private object MailSpec : ConfigSpec("mail") {
        val enabled by required<Boolean>()
        val server by required<String>()
        val port by required<Int>()
    }

    data class Mail(
        val enabled: Boolean = config[MailSpec.enabled],
        val server: String = config[MailSpec.server],
        val port: Int = config[MailSpec.port]
    )

    private object ExternalSpec : ConfigSpec("external") {
        val smmryApiKey by optional<String?>(null)
        val youtubeDlHost by required<String>()
        val scraperHost by optional<String?>(null)
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
        addSpec(ResourceSpec)
        addSpec(MailSpec)
        addSpec(ExternalSpec)
    }
        .from.json.resource("default.json")
        .from.json.resource("${mode.toString().lowercase()}.json")
        .from.json.file(System.getProperty("CONFIG_FILE") ?: "lynks.config.json", optional = true)
        .from.env()
        .from.systemProperties()

    val server = Server()
    val resource = Resource()
    val mail = Mail()
    val external = External()
}
