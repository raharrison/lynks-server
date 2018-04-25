package task

import common.TaskDefinition
import entry.EntryService
import entry.LinkService
import worker.WorkerRegistry
import kotlin.reflect.full.createInstance

class TaskService(private val entryService: EntryService,
                  private val linkService: LinkService,
                  private val workerRegistry: WorkerRegistry) {

    fun runTask(eid: String, taskId: String): Boolean {
        entryService.get(eid)?.let {
            it.props.getTask(taskId)?.let {
                val task = convertToConcreteTask(it)
                val context = TaskContext(taskId, eid, it.input)
                workerRegistry.acceptTaskWork(task, context)
                return true
            }
        }
        return false
    }

    private fun convertToConcreteTask(taskDefinition: TaskDefinition): Task {
        val clazz = Class.forName(taskDefinition.className).kotlin
        return (clazz.createInstance() as Task).also(::autowire)
    }

    private fun autowire(task: Task) {
        if(task is LinkProcessingTask) {
            task.linkService = linkService
            task.workerRegistry = workerRegistry
        }
    }

}