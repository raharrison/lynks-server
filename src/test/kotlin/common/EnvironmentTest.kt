package common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import util.JsonMapper

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
        val file = this.javaClass.getResource("/test.json")?.readText()
        val node = JsonMapper.defaultMapper.readTree(file).get("server")

        val db = node.get("database").textValue()
        val defaultPort = 8080

        assertThat(db).isEqualTo(Environment.server.database)
        assertThat(defaultPort).isEqualTo(Environment.server.port)
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

        val smmryApiKey = node.get("smmryApiKey").textValue()

        assertThat(smmryApiKey).isEqualTo(Environment.external.smmryApikey)
    }

}
