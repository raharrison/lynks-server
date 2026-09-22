package lynks.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit
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

    @Test
    fun testDeleteOlderThanUsesFileAgeInSharedDirectory() {
        val root = Files.createTempDirectory("lynks-cleanup")
        val shared = Files.createDirectory(root.resolve("shared"))
        val stale = Files.writeString(shared.resolve("stale.png"), "old")
        val fresh = Files.writeString(shared.resolve("fresh.png"), "new")
        age(stale)

        assertThat(FileUtils.deleteOlderThan(root, 5)).isEqualTo(1)
        assertThat(Files.exists(stale)).isFalse()
        assertThat(Files.exists(fresh)).isTrue()
    }

    @Test
    fun testDeleteOlderThanRemovesStaleDirectoriesOnly() {
        val root = Files.createTempDirectory("lynks-cleanup")
        val staleDir = Files.createDirectory(root.resolve("stale"))
        age(Files.writeString(staleDir.resolve("page.html"), "old"))
        age(staleDir)
        val freshDir = Files.createDirectory(root.resolve("fresh"))

        FileUtils.deleteOlderThan(root, 5)

        assertThat(Files.exists(staleDir)).isFalse()
        assertThat(Files.exists(freshDir)).isTrue()
        assertThat(Files.exists(root)).isTrue()
    }

    private fun age(path: Path) {
        Files.setLastModifiedTime(path, FileTime.from(Instant.now().minus(10, ChronoUnit.DAYS)))
    }

}
