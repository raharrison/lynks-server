package common

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec

enum class ConfigMode {
    DEV, TEST, PROD
}

object Environment {

    private object ServerSpec: ConfigSpec("server") {
        val database by required<String>()
        val driver by required<String>()
        val port by optional(8080)
        val resourceBasePath by required<String>()
        val resourceTempPath by required<String>()
    }

    val mode = ConfigMode.valueOf(System.getProperty("CONFIG_MODE") ?: "DEV")

    private val config = Config { addSpec(ServerSpec)}
            .withSourceFrom.json.resource("${mode.toString().toLowerCase()}.json")
            .withSourceFrom.env()
            .withSourceFrom.systemProperties()

    val database = config[ServerSpec.database]

    val driver = config[ServerSpec.driver]

    val port = config[ServerSpec.port]

    val resourceBasePath = config[ServerSpec.resourceBasePath]

    val resourceTempPath = config[ServerSpec.resourceTempPath]
}