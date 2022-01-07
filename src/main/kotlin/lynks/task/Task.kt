package lynks.task

import lynks.common.IdBasedCreatedEntity
import lynks.common.TaskParameter
import kotlin.reflect.KClass

abstract class Task<T : TaskContext>(override val id: String, val entryId: String) : IdBasedCreatedEntity {

    abstract suspend fun process(context: T)

    abstract fun createContext(params: Map<String, String>): T
}

open class TaskContext(private val input: Map<String, String> = emptyMap()) {

    fun param(field: String) = input[field] ?: error("Could not find param with key: $field")

    fun optParam(field: String) = input[field]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TaskContext
        return input == other.input
    }

    override fun hashCode(): Int {
        return input.hashCode()
    }

}

data class TaskBuilder(val clazz: KClass<out Task<*>>, val params: List<TaskParameter> = emptyList())
