package util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FileUtilsTest {

    @Test
    fun testGenerateTempFileName() {
        val iterations = 100
        val set = (0 until iterations).map { FileUtils.createTempFileName("src") }.toSet()
        assertThat(set).hasSize(1)
        set.forEach {assertThat(32).isEqualTo(it.length) }
    }

    @Test
    fun testRemoveExtension() {
        assertThat(FileUtils.removeExtension("file.webm")).isEqualTo("file")
        assertThat(FileUtils.removeExtension("file.jpg")).isEqualTo("file")
        assertThat(FileUtils.removeExtension("file")).isEqualTo("file")
    }

    @Test
    fun testGetExtension() {
        assertThat(FileUtils.getExtension("file.webm")).isEqualTo("webm")
        assertThat(FileUtils.getExtension("file.jpg")).isEqualTo("jpg")
        assertThat(FileUtils.getExtension("file")).isEqualTo("")
    }
}