package common

data class TaskDefinition(val id: String, val className: String, val input: Map<String, String> = emptyMap())