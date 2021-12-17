package lynks.common

data class TaskDefinition(override val id: String,
                          val description: String,
                          val className: String,
                          val input: Map<String, String> = emptyMap()): IdBasedCreatedEntity
