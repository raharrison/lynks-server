package lynks.resource

import lynks.common.ResourceId
import java.nio.file.Path

data class PendingResource(val id: ResourceId, val resourceType: ResourceType, val tempPath: Path)
