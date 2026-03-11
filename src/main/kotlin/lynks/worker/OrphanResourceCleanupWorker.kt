package lynks.worker

import kotlinx.coroutines.time.delay
import lynks.common.Environment
import lynks.resource.ResourceVersions
import lynks.util.FileUtils
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import kotlin.io.path.exists

data class OrphanResourceCleanupRequest(val intervalHours: Int = 24)

class OrphanResourceCleanupWorker : ChannelBasedWorker<OrphanResourceCleanupRequest>() {

    override suspend fun beforeWork() {
        super.onChannelReceive(OrphanResourceCleanupRequest())
    }

    override suspend fun doWork(input: OrphanResourceCleanupRequest) {
        val interval = Duration.ofHours(input.intervalHours.toLong())
        delay(interval)
        while (true) {
            try {
                reconcileOrphans()
            } finally {
                delay(interval)
            }
        }
    }

    private fun reconcileOrphans() {
        val basePath = Paths.get(Environment.resource.resourceBasePath)
        if (!basePath.exists()) return

        val filesByResourceId = Files.walk(basePath).use { stream ->
            stream.filter { Files.isRegularFile(it) }.toList()
        }.associateBy { FileUtils.removeExtension(it.fileName.toString()) }

        if (filesByResourceId.isEmpty()) return

        val knownIds = transaction {
            ResourceVersions.select(ResourceVersions.id)
                .where { ResourceVersions.id inList filesByResourceId.keys.toList() }
                .map { it[ResourceVersions.id] }
                .toSet()
        }

        val orphans = filesByResourceId.filterKeys { it !in knownIds }.values
        if (orphans.isNotEmpty()) {
            log.info("Orphan resource cleanup removing {} orphaned files", orphans.size)
            orphans.forEach { Files.deleteIfExists(it) }
        } else {
            log.debug("Orphan resource cleanup found no orphaned files")
        }
    }
}
