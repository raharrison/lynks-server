package lynks.resource

import lynks.common.Environment
import lynks.common.IMAGE_UPLOAD_BASE
import lynks.common.TEMP_URL
import lynks.util.markdown.MarkdownNodeVisitor
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension

class TempImageMarkdownVisitor(
    private val eid: String,
    private val resourceManager: ResourceManager
) : MarkdownNodeVisitor {

    override val pattern: String = """!\[.*?]\(($TEMP_URL.+?)\)"""

    override fun replace(match: MatchResult): String? {
        val url = match.groups[1]?.value ?: return null
        val file = resourceManager.constructTempBasePath(IMAGE_UPLOAD_BASE).resolve(Path.of(url).fileName)
        val generatedResource = GeneratedResource(ResourceType.UPLOAD, file.absolutePathString(), file.extension)
        val migrated = resourceManager.migrateGeneratedResources(eid, listOf(generatedResource))
        return migrated.firstOrNull()?.let {
            return match.value.replace(url, "${Environment.server.rootPath}/entry/$eid/resource/${it.id}")
        }
    }

}
