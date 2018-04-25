package task

interface Task {
    suspend fun process(context: TaskContext)
}

open class TaskContext(val taskId: String, val entryId: String, val input: Map<String, String> = emptyMap())
