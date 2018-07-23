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
        val file = this.javaClass.getResource("/test.json").readText()
        val node = JsonMapper.defaultMapper.readTree(file).get("server")

        val db = node.get("database").textValue()
        val driver = node.get("driver").textValue()
        val resPath = node.get("resourceBasePath").textValue()
        val tempPath = node.get("resourceTempPath").textValue()

        assertThat(db).isEqualTo(Environment.server.database)
        assertThat(driver).isEqualTo(Environment.server.driver)
        assertThat(Environment.server.port).isEqualTo(8080)
        assertThat(resPath).isEqualTo(Environment.server.resourceBasePath)
        assertThat(tempPath).isEqualTo(Environment.server.resourceTempPath)
    }

    @Test
    fun testEnvironmentMailProperties() {
        val file = this.javaClass.getResource("/test.json").readText()
        val node = JsonMapper.defaultMapper.readTree(file).get("mail")

        val enabled = node.get("enabled").booleanValue()
        val server = node.get("server").textValue()
        val port = node.get("port").intValue()

        assertThat(enabled).isEqualTo(Environment.mail.enabled)
        assertThat(server).isEqualTo(Environment.mail.server)
        assertThat(port).isEqualTo(Environment.mail.port)
    }

}