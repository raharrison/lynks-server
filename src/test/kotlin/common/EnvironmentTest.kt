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
    fun testEnvironmentProperties() {
        val file = this.javaClass.getResource("/test.json").readText()
        val node = JsonMapper.defaultMapper.readTree(file).get("server")

        val db = node.get("database").textValue()
        val driver = node.get("driver").textValue()

        assertThat(db).isEqualTo(Environment.database)
        assertThat(driver).isEqualTo(Environment.driver)
        assertThat(Environment.port).isEqualTo(8080)

    }

}