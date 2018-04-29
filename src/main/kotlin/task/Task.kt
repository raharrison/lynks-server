package task

abstract class Task(val id: String, val entryId: String) {

    abstract suspend fun process(context: TaskContext)

}

data class TaskContext(val input: Map<String, String> = emptyMap())
