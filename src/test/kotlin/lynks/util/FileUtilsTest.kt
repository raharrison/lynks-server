package lynks.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

class FileUtilsTest {

    @Test
    fun testGenerateTempFileName() {
        val iterations = 100
        val set = (0 until iterations).map { FileUtils.createTempFileName("src") }.toSet()
        assertThat(set).hasSize(1)
        set.forEach {assertThat(64).isEqualTo(it.length) }
    }

    @Test
    fun testCreateTempFileNameConcurrency() {
        val expected = FileUtils.createTempFileName("concurrent-test")
        val pool = Executors.newFixedThreadPool(8)
        val futures = (1..50).map { pool.submit<String> { FileUtils.createTempFileName("concurrent-test") } }
        pool.shutdown()
        assertThat(futures.map { it.get() }).allMatch { it == expected }
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
