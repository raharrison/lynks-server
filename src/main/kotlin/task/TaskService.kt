package task

import common.TaskDefinition
import common.inject.Inject
import common.inject.ServiceProvider
import entry.EntryService
import worker.WorkerRegistry
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

class TaskService(private val entryService: EntryService,
                  private val serviceProvider: ServiceProvider,
                  private val workerRegistry: WorkerRegistry) {

    fun runTask(eid: String, taskId: String): Boolean {
        entryService.get(eid)?.let { it ->
            it.props.getTask(taskId)?.let {
                val task = convertToConcreteTask(taskId, eid, it)
                workerRegistry.acceptTaskWork(task, task.createContext(it.input))
                return true
            }
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertToConcreteTask(taskId: String, eid: String, def: TaskDefinition): Task<TaskContext> {
        val clazz = Class.forName(def.className).kotlin
        return (clazz.primaryConstructor?.call(taskId, eid) as Task<TaskContext>).also(::autowire)
    }

    private fun autowire(task: Task<TaskContext>) {
        task::class.memberProperties.forEach {
            if(it.findAnnotation<Inject>() != null) {
                if(it is KMutableProperty1) {
                    val service = serviceProvider.get(it.returnType.jvmErasure)
                    if(service != null)
                        it.setter.call(task, service)
                }
            }
        }
    }

}