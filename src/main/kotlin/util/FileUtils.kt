package util

import com.google.common.hash.Hashing
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

object FileUtils {

    fun writeToFile(path: Path, data: ByteArray) {
        val parentPath = path.parent
        if(!Files.exists(parentPath))
            Files.createDirectories(parentPath)
        Files.write(path, data)
    }

    fun createTempFileName(src: String): String {
        return Hashing.murmur3_128().hashString(src, Charset.defaultCharset()).toString()
    }

    fun removeExtension(name: String): String {
        return com.google.common.io.Files.getNameWithoutExtension(name)
    }

    // get temp files older than 2 days
    // delete files
    // move files

}
