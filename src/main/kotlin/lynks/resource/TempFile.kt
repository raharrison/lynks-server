package lynks.resource

import lynks.util.FileUtils
import java.nio.file.Files
import java.nio.file.Path

class TempFile(val src: String, val extension: String, private val tempPath: Path) : AutoCloseable {

    private var closed: Boolean

    init {
        val parentPath = tempPath.parent
        if (!Files.exists(parentPath))
            Files.createDirectories(parentPath)
        closed = false
    }

    val path: Path get() {
        if(closed) {
            throw IllegalStateException("Temp file has been closed")
        }
        return tempPath
    }

    override fun close() {
        FileUtils.deleteWithParentIfEmpty(tempPath)
        closed = true
    }

}
