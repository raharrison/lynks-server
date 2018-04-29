package task

import common.TaskDefinition
import entry.EntryService
import entry.LinkService
import worker.WorkerRegistry
import kotlin.reflect.full.primaryConstructor

class TaskService(private val entryService: EntryService,
                  private val linkService: LinkService,
                  private val workerRegistry: WorkerRegistry) {

    fun runTask(eid: String, taskId: String): Boolean {
        entryService.get(eid)?.let {
            it.props.getTask(taskId)?.let {
                val task = convertToConcreteTask(taskId, eid, it)
                val context = TaskContext(it.input)
                workerRegistry.acceptTaskWork(task, context)
                return true
            }
        }
        return false
    }

    private fun convertToConcreteTask(taskId: String, eid: String, def: TaskDefinition): Task {
        val clazz = Class.forName(def.className).kotlin
        return (clazz.primaryConstructor?.call(taskId, eid) as Task).also(::autowire)
    }

    private fun autowire(task: Task) {
        if(task is LinkProcessingTask) {
            task.linkService = linkService
            task.workerRegistry = workerRegistry
        }
    }

}