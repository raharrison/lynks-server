package lynks.worker

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lynks.common.Environment
import lynks.entry.EntryAuditService
import lynks.notify.NotifyService
import lynks.user.Preferences
import lynks.user.UserService
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

    private val userService = mockk<UserService>()
    private val notifyService = mockk<NotifyService>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)

    @AfterEach
    fun cleanUp() {
        Paths.get(Environment.resource.resourceTempPath).toFile().deleteRecursively()
    }

    @BeforeEach
    fun createFiles() {
        createTempFiles()
        val preferences = Preferences()
        every { userService.currentUserPreferences } returns preferences
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
        val worker = TempFileCleanupWorker(userService, notifyService, entryAuditService)
            .apply { runner = this@runTest.coroutineContext }
        worker.worker()

        advanceTimeBy(6 * 1000 * 60 * 60)
        runCurrent()

        assertThat(Files.exists(newFile)).isTrue()
        assertThat(Files.exists(oldFile)).isFalse()

        worker.cancelAll()
    }

    @Test
    fun testOverrideDefaultTimeout() = runTest {
        val worker = TempFileCleanupWorker(userService, notifyService, entryAuditService)
            .apply { runner = this@runTest.coroutineContext }
        val send = worker.worker()
        runCurrent()

        send.trySendBlocking(TempFileCleanupWorkerRequest(Preferences(tempFileCleanInterval = 2)))

        createTempFiles() // recreate files
        assertThat(Files.exists(newFile)).isTrue()
        assertThat(Files.exists(oldFile)).isTrue()

        advanceTimeBy(2 * 1000 * 60 * 60)
        runCurrent()

        assertThat(Files.exists(newFile)).isTrue()
        assertThat(Files.exists(oldFile)).isFalse()

        worker.cancelAll()
        send.close()
    }

}
