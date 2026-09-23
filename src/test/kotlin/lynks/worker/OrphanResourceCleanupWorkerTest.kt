package lynks.worker

import lynks.common.*
import lynks.resource.ResourceRepository
import lynks.resource.ResourceType
import lynks.util.createDummyEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit

class OrphanResourceCleanupWorkerTest : DatabaseTest() {

    private val entryDir = Paths.get(Environment.resource.resourceBasePath, "e", "ei", "eid")
    private val knownFile = entryDir.resolve("known.png")
    private val orphanFile = entryDir.resolve("orphan.png")
    private val youngOrphanFile = entryDir.resolve("young.png")
    private val tempFile = Paths.get(Environment.resource.resourceTempPath, TEMP_UPLOAD_DIR, "upload.png")

    @BeforeEach
    fun createFiles() {
        createDummyEntry("eid", "title", "content", EntryType.LINK)
        ResourceRepository().createOrUpdateRecord(ResourceId("known"), EntryId("eid"), "known.png", "png", ResourceType.UPLOAD, 1)
        listOf(knownFile, orphanFile, youngOrphanFile, tempFile).forEach {
            Files.createDirectories(it.parent)
            Files.write(it, byteArrayOf(1))
        }
        listOf(knownFile, orphanFile, tempFile).forEach(::age)
    }

    @AfterEach
    fun cleanUp() {
        Paths.get(Environment.resource.resourceBasePath).toFile().deleteRecursively()
    }

    private fun age(path: Path) {
        Files.setLastModifiedTime(path, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)))
    }

    @Test
    fun testRemovesOnlyOldUnknownFilesOutsideTemp() {
        val removed = OrphanResourceCleanupWorker().reconcileOrphans()

        assertThat(removed).isOne()
        assertThat(orphanFile).doesNotExist()
        assertThat(knownFile).exists()
        assertThat(youngOrphanFile).exists()
        assertThat(tempFile).exists()
    }
}
