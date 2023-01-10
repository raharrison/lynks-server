package lynks.common

data class TaskDefinition(
    override val id: String,
    val description: String,
    val className: String,
    val params: List<TaskParameter> = emptyList()
) : IdBasedCreatedEntity

enum class TaskParameterType {
    BOOL, TEXT, NUMBER, ENUM, STATIC, MULTI
}

data class TaskParameter(
    val name: String,
    val type: TaskParameterType,
    val description: String? = null,
    val value: String? = null,
    val options: List<String>? = null,
    val required: Boolean = true
)
