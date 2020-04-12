package resource

import util.FileUtils
import java.nio.file.Files
import java.nio.file.Path

class TempFile(val src: String, val extension: String, val path: Path) : AutoCloseable {

    init {
        val parentPath = path.parent
        if (!Files.exists(parentPath))
            Files.createDirectories(parentPath)
    }

    override fun close() {
        FileUtils.deleteWithParentIfEmpty(path)
    }

}