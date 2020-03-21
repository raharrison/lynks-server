package common

import task.TaskBuilder
import util.RandomUtils

open class BaseProperties {

    val attributes: MutableMap<String, Any>
    val tasks: MutableList<TaskDefinition>

    constructor(): this(mutableMapOf(), mutableListOf())

    private constructor(attrs: MutableMap<String, Any>, tasks: MutableList<TaskDefinition>) {
        this.attributes = attrs
        this.tasks = tasks
    }

    fun addAttribute(key: String, value: Any) {
        attributes[key] = value
    }

    fun getAttribute(key: String): Any? = attributes[key]

    fun containsAttribute(key: String) = attributes.contains(key)

    fun removeAttribute(key: String) = attributes.remove(key)

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

    fun merge(newProps: BaseProperties): BaseProperties {
        val mergedAttributes = this.attributes.toMutableMap() + newProps.attributes
        val mergedTasks = this.tasks.associateBy { it.description } + newProps.tasks.associateBy { it.description }
        return BaseProperties(mergedAttributes.toMutableMap(), mergedTasks.values.toMutableList())
    }
}
