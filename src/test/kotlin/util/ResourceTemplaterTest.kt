package util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException

class ResourceTemplaterTest {

    @Test
    fun testTemplate() {
        val templater = ResourceTemplater("test-template.txt")
        val replace = "Bill"
        val input = mapOf("name" to replace)
        val expected = "Hello $replace!"
        val result = templater.apply(input)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun testInvalidTemplate() {
        assertThrows<FileNotFoundException> { ResourceTemplater("invalid") }
    }

}