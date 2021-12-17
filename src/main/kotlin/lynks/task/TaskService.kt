package lynks.task

import lynks.common.TaskDefinition
import lynks.common.inject.Inject
import lynks.common.inject.ServiceProvider
import lynks.entry.EntryService
import lynks.util.loggerFor
import lynks.worker.WorkerRegistry
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

class TaskService(private val entryService: EntryService,
                  private val serviceProvider: ServiceProvider,
                  private val workerRegistry: WorkerRegistry
) {

    private val log = loggerFor<TaskService>()

    fun runTask(eid: String, taskId: String): Boolean {
        entryService.get(eid)?.let { it ->
            it.props.getTask(taskId)?.let {
                val task = convertToConcreteTask(taskId, eid, it)
                log.info("Submitting task work request for entry={} task={}", eid, taskId)
                workerRegistry.acceptTaskWork(task, task.createContext(it.input))
                return true
            }
        }
        log.info("Could not run task as either entry or task not found entry={} task={}", eid, taskId)
        return false
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertToConcreteTask(taskId: String, eid: String, def: TaskDefinition): Task<TaskContext> {
        val clazz = Class.forName(def.className).kotlin
        if (clazz.isSubclassOf(Task::class)) {
            return (clazz.primaryConstructor?.call(taskId, eid) as Task<TaskContext>).also(::autowire)
        } else {
            throw IllegalArgumentException("Task must be a subclass of: " + Task::class.qualifiedName)
        }
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
