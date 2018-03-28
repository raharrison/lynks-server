package common

open class BaseProperties {

    private val attributes = mutableMapOf<String, String>()

    fun addAttribute(key: String, value: String) {
        attributes[key] = value
    }

    fun getAttribute(key: String): String? = attributes[key]

    fun contains(key: String) = attributes.contains(key)
}
