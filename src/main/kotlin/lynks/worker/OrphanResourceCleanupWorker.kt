package lynks.worker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.time.delay
import lynks.common.Environment
import lynks.resource.ResourceVersions
import lynks.util.FileUtils
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import kotlin.io.path.exists

data class OrphanResourceCleanupRequest(val intervalHours: Int = 24)

class OrphanResourceCleanupWorker : ChannelBasedWorker<OrphanResourceCleanupRequest>() {

    // A resource file is written before its record commits, so a young file may just be mid-save
    private val minOrphanAge = Duration.ofDays(1)

    // Postgres caps a statement at 65535 bind parameters
    private val idBatchSize = 1000

    override suspend fun beforeWork() {
        super.onChannelReceive(OrphanResourceCleanupRequest())
    }

    override suspend fun doWork(input: OrphanResourceCleanupRequest) {
        val interval = Duration.ofHours(input.intervalHours.toLong())
        delay(interval)
        while (true) {
            try {
                reconcileOrphans()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Orphan resource cleanup failed", e)
            }
            delay(interval)
        }
    }

    internal fun reconcileOrphans(): Int {
        val basePath = Paths.get(Environment.resource.resourceBasePath).toAbsolutePath().normalize()
        if (!basePath.exists()) return 0
        // the temp directory lives under the base path by default and holds files with no resource record yet
        val tempPath = Paths.get(Environment.resource.resourceTempPath).toAbsolutePath().normalize()
        val cutoff = Instant.now().minus(minOrphanAge)

        val filesByResourceId = Files.walk(basePath).use { stream ->
            stream.filter { !it.startsWith(tempPath) && Files.isRegularFile(it) && isOlderThan(it, cutoff) }.toList()
        }.groupBy { FileUtils.removeExtension(it.fileName.toString()) }

        if (filesByResourceId.isEmpty()) return 0

        val knownIds = filesByResourceId.keys.chunked(idBatchSize).flatMap { batch ->
            transaction {
                ResourceVersions.select(ResourceVersions.id)
                    .where { ResourceVersions.id inList batch }
                    .map { it[ResourceVersions.id] }
            }
        }.toSet()

        val orphans = filesByResourceId.filterKeys { it !in knownIds }.values.flatten()
        if (orphans.isNotEmpty()) {
            log.info("Orphan resource cleanup removing {} orphaned files", orphans.size)
            orphans.forEach { Files.deleteIfExists(it) }
        } else {
            log.debug("Orphan resource cleanup found no orphaned files")
        }
        return orphans.size
    }

    private fun isOlderThan(path: Path, cutoff: Instant): Boolean =
        runCatching { Files.getLastModifiedTime(path).toInstant().isBefore(cutoff) }.getOrDefault(false)
}
