package lynks.util

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

    // One pass, so a replacement is never re-matched: "&amp;lt;" becomes "&lt;", not "<"
    private val entityPattern = entities.keys.joinToString("|") { Regex.escape(it) }.toRegex()

    private val stopwords : Set<String> by lazy {
        javaClass.getResource("/stopwords/stopwords.txt")
            .readText().lines().toSet()
    }

    private fun normalizeEntities(str: String): String {
        return entityPattern.replace(str) { entities.getValue(it.value) }
    }

    // replace entities and remove all stopwords
    fun normalize(str: String): String {
        if (str.isBlank()) {
            return ""
        }
        val replaced = normalizeEntities(str).replace("\\p{Punct}".toRegex(), "")
        return replaced.lowercase().splitToSequence("\\s+".toRegex())
            .filterNot { it.isBlank() }
            .filterNot { stopwords.contains(it) }
            .joinToString(" ")
    }

    fun mostCommonWords(str: String, n: Int): String {
        val words = str.split("\\s+".toRegex())
        val mostCommon = words
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }
            .toSet()
        return words
            .filter { mostCommon.contains(it) }
            .joinToString(" ")
    }

    fun convertToDbColumnName(str: String?): String? {
        if(str == null) return null
        val sb = StringBuilder(str.length)
        str.forEach {
            if (Character.isUpperCase(it)) {
                sb.append("_").append(it)
            } else {
                sb.append(it)
            }
        }
        return sb.toString()
    }

}
