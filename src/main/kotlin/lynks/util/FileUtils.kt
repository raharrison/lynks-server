package lynks.util

import java.io.IOException
import java.math.BigInteger
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.name

object FileUtils {

    fun writeToFile(path: Path, data: ByteArray) {
        val parentPath = path.parent
        if (!Files.exists(parentPath))
            Files.createDirectories(parentPath)
        Files.write(path, data)
    }

    fun moveFile(from: Path, to: Path) {
        val parentPath = to.parent
        if (!Files.exists(parentPath))
            Files.createDirectories(parentPath)
        Files.move(from, to)
    }

    fun deleteWithParentIfEmpty(path: Path) {
        Files.deleteIfExists(path)
        if (path.parent.toFile().list()?.isEmpty() == true) {
            Files.delete(path.parent)
        }
    }

    fun createTempFileName(src: String): String {
        return BigInteger(1, MessageDigest.getInstance("SHA-256").digest(src.toByteArray()))
            .toString(16).padStart(32, '0')
    }

    fun removeExtension(name: String): String {
        val fileName = getFileName(name)
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex == -1) fileName else fileName.substring(0, dotIndex)
    }

    fun getExtension(name: String): String {
        val fileName = getFileName(name)
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex == -1) "" else fileName.substring(dotIndex + 1)
    }

    fun getFileName(str: String): String {
        return Path.of(str).name
    }

    // Walks by file age because uploads share one directory whose timestamp every upload refreshes.
    // A directory's own timestamp is read before its contents are deleted, so one being filled right now survives.
    fun deleteOlderThan(root: Path, days: Long): Int {
        val cutoff = Instant.now().minus(days, ChronoUnit.DAYS)
        val staleDirs = mutableSetOf<Path>()
        var deleted = 0
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != root && attrs.lastModifiedTime().toInstant().isBefore(cutoff)) staleDirs.add(dir)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.lastModifiedTime().toInstant().isBefore(cutoff) && Files.deleteIfExists(file)) deleted++
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (dir in staleDirs && dir.toFile().list()?.isEmpty() == true) Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
        return deleted
    }

    fun deleteDirectories(dirs: List<Path>) {
        dirs.forEach {
            it.toFile().deleteRecursively()
        }
    }

}
