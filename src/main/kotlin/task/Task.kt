package task

import kotlin.reflect.KClass

abstract class Task<T: TaskContext>(val id: String, val entryId: String) {

    abstract suspend fun process(context: T)

    abstract fun createContext(input: Map<String, String>): T
}

open class TaskContext(val input: Map<String, String> = emptyMap()) {

    protected fun param(field: String) = input[field]!!

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

data class TaskBuilder(val clazz: KClass<out Task<*>>, val context: TaskContext)
