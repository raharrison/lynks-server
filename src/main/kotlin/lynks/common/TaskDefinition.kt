package lynks.common

data class TaskDefinition(
    override val id: String,
    val description: String,
    val className: String,
    val params: List<TaskParameter> = emptyList()
) : IdBasedCreatedEntity

enum class TaskParameterType {
    TEXT, NUMBER, ENUM, STATIC
}

data class TaskParameter(
    val name: String,
    val type: TaskParameterType,
    val description: String? = null,
    val value: String? = null,
    val options: Set<String>? = null
)
