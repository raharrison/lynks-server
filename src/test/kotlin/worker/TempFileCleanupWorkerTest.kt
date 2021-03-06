package worker

import common.Environment
import entry.EntryAuditService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineContext
import notify.NotifyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import user.Preferences
import user.UserService
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

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

    private val context = TestCoroutineContext()

    @Test
    fun testWorkerRemovesOldFilesOnStartupAfterInitialDelay() = runBlocking(context) {
        val worker = TempFileCleanupWorker(userService, notifyService, entryAuditService)
            .apply { runner = context }.worker()

        context.advanceTimeBy(6, TimeUnit.HOURS)
        worker.close()

        assertThat(Files.exists(newFile)).isTrue()
        assertThat(Files.exists(oldFile)).isFalse()
        Unit
    }

    @Test
    fun testOverrideDefaultTimeout() = runBlocking(context) {
        val worker = TempFileCleanupWorker(userService, notifyService, entryAuditService)
            .apply { runner = context }.worker()
        context.triggerActions() // initial trigger

        worker.offer(TempFileCleanupWorkerRequest(Preferences(tempFileCleanInterval = 2)))

        createTempFiles() // recreate files
        assertThat(Files.exists(newFile)).isTrue()
        assertThat(Files.exists(oldFile)).isTrue()

        context.advanceTimeBy(2, TimeUnit.HOURS)
        worker.close()

        assertThat(Files.exists(newFile)).isTrue()
        assertThat(Files.exists(oldFile)).isFalse()
        Unit
    }

}
