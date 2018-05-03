package common

import task.TaskBuilder
import util.RandomUtils

open class BaseProperties {

    val attributes = mutableMapOf<String, String>()
    val tasks = mutableListOf<TaskDefinition>()

    fun addAttribute(key: String, value: String) {
        attributes[key] = value
    }

    fun getAttribute(key: String): String? = attributes[key]

    fun containsAttribute(key: String) = attributes.contains(key)

    fun addTask(task: TaskDefinition) {
        tasks.add(task)
    }

    fun addTask(description: String, task: TaskBuilder) {
        tasks.add(TaskDefinition(RandomUtils.generateUid(), description, task.clazz.qualifiedName!!, task.context.input))
    }

    fun getTask(id: String): TaskDefinition? = tasks.find { it.id == id }
}
