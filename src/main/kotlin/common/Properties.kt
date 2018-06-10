package common

import task.TaskBuilder
import util.RandomUtils

open class BaseProperties {

    val attributes = mutableMapOf<String, Any>()
    val tasks = mutableListOf<TaskDefinition>()

    fun addAttribute(key: String, value: Any) {
        attributes[key] = value
    }

    fun getAttribute(key: String): Any? = attributes[key]

    fun containsAttribute(key: String) = attributes.contains(key)

    fun addTask(task: TaskDefinition) {
        val index = tasks.indexOfFirst { it.description == task.description }
        if(index > -1)
            tasks[index] = task
        else
            tasks.add(task)
    }

    fun addTask(description: String, task: TaskBuilder): TaskDefinition {
        val definition = TaskDefinition(RandomUtils.generateUid(), description, task.clazz.qualifiedName!!, task.context.input)
        addTask(definition)
        return definition
    }

    fun getTask(id: String): TaskDefinition? = tasks.find { it.id == id }
}
