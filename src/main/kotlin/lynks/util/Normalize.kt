package lynks.util

import org.apache.commons.lang3.StringUtils

object Normalize {

    private val entities = mapOf(
        "\u2014" to "-",
        "\u2013" to "-",
        "&mdash;" to "-",
        "&ndash;" to "-",
        "\u00A0" to " ",
        "&nbsp;" to " ",
        "\u00AB" to "\"",
        "\u00BB" to "\"",
        "&quot;" to "\"",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">"
    )

    private fun normalizeEntities(str: String): String {
        return StringUtils.replaceEach(str, entities.keys.toTypedArray(), entities.values.toTypedArray())
    }

    private fun normalizeSpaces(str: String): String {
        if (StringUtils.isBlank(str)) {
            return ""
        }
        return str.split("\\s+".toRegex()).joinToString(" ")
    }

    fun normalize(str: String): String {
        return normalizeEntities(normalizeSpaces(str)).trim()
    }

    fun removeStopwords(str: String): String {
        val stopwords = javaClass.getResource("/stopwords/stopwords.txt").readText().lines().toSet()
        val words = str.lowercase().split(" ").toMutableList()
        words.removeAll(stopwords)
        return words.joinToString(" ")
    }

}
