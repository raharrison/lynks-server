package common

open class BaseProperties {

    private val attributes = mutableMapOf<String, String>()
    private val tasks = mutableMapOf<String, TaskDefinition>()

    fun addAttribute(key: String, value: String) {
        attributes[key] = value
    }

    fun getAttribute(key: String): String? = attributes[key]

    fun containsAttribute(key: String) = attributes.contains(key)

    fun addTask(task: TaskDefinition) {
        tasks[task.id] = task
    }

    fun getTask(id: String): TaskDefinition? = tasks[id]
}
