package lynks.common

import lynks.util.JsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EnvironmentTest {

    // all tests have CONFIG_MODE=TEST -> test.json

    @Test
    fun testEnvironmentMode() {
        val curr = System.getProperty("CONFIG_MODE")
        assertThat(curr).isEqualTo("TEST")
        assertThat(Environment.mode).isEqualTo(ConfigMode.TEST)
    }

    @Test
    fun testEnvironmentServerProperties() {
        val defaultPort = 8080
        assertThat(defaultPort).isEqualTo(Environment.server.port)
    }

    @Test
    fun testEnvironmentDatabaseProperties() {
        assertThat(Environment.database.url).startsWith("jdbc:postgresql://")
    }

    @Test
    fun testEnvironmentResourceProperties() {
        val file = this.javaClass.getResource("/test.json")?.readText()
        val node = JsonMapper.defaultMapper.readTree(file).get("resource")

        val resPath = node.get("resourceBasePath").textValue()
        val tempPath = node.get("resourceTempPath").textValue()

        assertThat(resPath).isEqualTo(Environment.resource.resourceBasePath)
        assertThat(tempPath).isEqualTo(Environment.resource.resourceTempPath)
    }

    @Test
    fun testEnvironmentExternalProperties() {
        val file = this.javaClass.getResource("/test.json")?.readText()
        val node = JsonMapper.defaultMapper.readTree(file).get("external")

        val scraperHost = node.get("scraperHost").textValue()
        val youtubeApiKey = node.get("youtubeApiKey").textValue()
        val youtubeApiBaseUrl = node.get("youtubeApiBaseUrl").textValue()
        val joltHost = node.get("joltHost").textValue()
        val joltToken = node.get("joltToken").textValue()

        assertThat(scraperHost).isEqualTo(Environment.external.scraperHost)
        assertThat(youtubeApiKey).isEqualTo(Environment.external.youtubeApiKey)
        assertThat(youtubeApiBaseUrl).isEqualTo(Environment.external.youtubeApiBaseUrl)
        assertThat(joltHost).isEqualTo(Environment.external.joltHost)
        assertThat(joltToken).isEqualTo(Environment.external.joltToken)
    }

}
