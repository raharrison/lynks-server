package util

import com.google.common.hash.Hashing
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.temporal.ChronoUnit

object FileUtils {

    fun writeToFile(path: Path, data: ByteArray) {
        val parentPath = path.parent
        if (!Files.exists(parentPath))
            Files.createDirectories(parentPath)
        Files.write(path, data)
    }

    fun deleteWithParentIfEmpty(path: Path) {
        Files.deleteIfExists(path)
        if (path.parent.toFile().list()?.isEmpty() == true) {
            Files.delete(path.parent)
        }
    }

    fun createTempFileName(src: String): String {
        return Hashing.murmur3_128().hashString(src, Charset.defaultCharset()).toString()
    }

    fun removeExtension(name: String): String {
        val fileName= File(name).name
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex == -1) fileName else fileName.substring(0, dotIndex)
    }

    fun getExtension(name: String): String {
        val fileName = File(name).name
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex == -1) "" else fileName.substring(dotIndex + 1)
    }

    fun directoriesOlderThan(path: Path, days: Long): List<Path> {
        val now = Instant.now().minus(days, ChronoUnit.DAYS)
        return Files.newDirectoryStream(path) {
            Files.readAttributes(it, BasicFileAttributes::class.java)
                .lastModifiedTime().toInstant().isBefore(now)
        }.use { it.toList() }
    }

    fun deleteDirectories(dirs: List<Path>) {
        dirs.forEach {
            it.toFile().deleteRecursively()
        }
    }

}
