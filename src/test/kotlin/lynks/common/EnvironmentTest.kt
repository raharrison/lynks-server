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
        val file = this.javaClass.getResource("/test.json")?.readText()
        val node = JsonMapper.defaultMapper.readTree(file).get("database")

        val dialect = node.get("dialect").textValue()
        val url = node.get("url").textValue()

        assertThat(dialect).isEqualTo(Environment.database.dialect.toString())
        assertThat(url).isEqualTo(Environment.database.url)
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
        val smmryApiKey = node.get("smmryApiKey").textValue()
        val pushoverToken = node.get("pushoverToken").textValue()
        val pushoverUser = node.get("pushoverUser").textValue()

        assertThat(scraperHost).isEqualTo(Environment.external.scraperHost)
        assertThat(smmryApiKey).isEqualTo(Environment.external.smmryApikey)
        assertThat(pushoverToken).isEqualTo(Environment.external.pushoverToken)
        assertThat(pushoverUser).isEqualTo(Environment.external.pushoverUser)
    }

}
