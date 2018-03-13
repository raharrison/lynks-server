package util

import com.google.common.hash.Hashing
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths

object FileUtils {

    fun writeToFile(path: String, data: ByteArray) {
        Files.write(Paths.get(path), data)
    }

    fun createTempFileName(src: String): String {
        return Hashing.goodFastHash(128).hashString(src, Charset.defaultCharset()).toString()
    }

    // get temp files older than 2 days
    // delete files
    // move files

}
