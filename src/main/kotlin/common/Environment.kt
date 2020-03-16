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
        val resourceBasePath by required<String>()
        val resourceTempPath by required<String>()
    }

    data class Server(
        val database: String = config[ServerSpec.database],
        val driver: String = config[ServerSpec.driver],
        val port: Int = config[ServerSpec.port],
        val resourceBasePath: String = config[ServerSpec.resourceBasePath],
        val resourceTempPath: String = config[ServerSpec.resourceTempPath]
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

    val mode: ConfigMode = ConfigMode.valueOf(System.getProperty("CONFIG_MODE")?.toUpperCase() ?: "DEV")

    init {
        log.info("Using config mode: $mode")
    }

    private val config = Config {
        addSpec(ServerSpec)
        addSpec(MailSpec)
    }
        .from.json.resource("${mode.toString().toLowerCase()}.json")
        .from.json.file(System.getProperty("CONFIG_FILE") ?: "lynks.config.json", optional = true)
        .from.env()
        .from.systemProperties()

    val server = Server()
    val mail = Mail()
}