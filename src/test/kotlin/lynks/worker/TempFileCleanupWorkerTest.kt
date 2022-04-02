package lynks.worker

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lynks.common.Environment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExperimentalCoroutinesApi
class TempFileCleanupWorkerTest {

    private val oldFile = Paths.get(Environment.resource.resourceTempPath, "e1", "f1")
    private val newFile = Paths.get(Environment.resource.resourceTempPath, "e2", "f2")

    @BeforeEach
    fun createFiles() {
        createTempFiles()
    }

    @AfterEach
    fun cleanUp() {
        Paths.get(Environment.resource.resourceTempPath).toFile().deleteRecursively()
    }

    private fun createTempFiles() {
        Paths.get(Environment.resource.resourceTempPath).toFile().deleteRecursively()
        Files.createDirectories(oldFile.parent)
        Files.createFile(oldFile)
        val oldTime = Instant.now().minus(15, ChronoUnit.DAYS)
        val attributes = Files.getFileAttributeView(oldFile.parent, BasicFileAttributeView::class.java)
        val time = FileTime.from(oldTime)
        attributes.setTimes(time, time, time)

        Files.createDirectories(newFile.parent)
        Files.createFile(newFile)
    }

    @Test
    fun testWorkerRemovesOldFilesOnStartupAfterInitialDelay() = runTest {
        val worker = TempFileCleanupWorker()
            .apply { runner = this@runTest.coroutineContext }
        val send = worker.worker()

        advanceTimeBy(6 * 1000 * 60 * 60)
        runCurrent()

        assertThat(Files.exists(newFile)).isTrue()
        assertThat(Files.exists(oldFile)).isFalse()

        send.close()
        worker.cancel()
    }

}
