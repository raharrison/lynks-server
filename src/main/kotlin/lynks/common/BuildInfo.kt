package lynks.common

import java.util.*

object BuildInfo {

    val version: String = Properties().apply {
        BuildInfo::class.java.classLoader.getResourceAsStream("version.properties")?.use { load(it) }
    }.getProperty("version", "unknown")

}
