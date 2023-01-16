package lynks.task

import lynks.common.TaskDefinition
import lynks.common.TaskParameterType
import lynks.common.exception.InvalidModelException
import lynks.common.inject.Inject
import lynks.common.inject.ServiceProvider
import lynks.entry.EntryService
import lynks.util.loggerFor
import lynks.worker.WorkerRegistry

class TaskService(private val entryService: EntryService,
                  private val serviceProvider: ServiceProvider,
                  private val workerRegistry: WorkerRegistry
) {

    private val log = loggerFor<TaskService>()

    fun runTask(eid: String, taskId: String, params: Map<String, String>): Boolean {
        entryService.get(eid)?.let { it ->
            it.props.getTask(taskId)?.let {
                val task = convertToConcreteTask(taskId, eid, it)
                log.info("Submitting task work request for entry={} task={}", eid, taskId)
                val taskParams = formTaskParams(it, params)
                workerRegistry.acceptTaskWork(task, task.createContext(taskParams))
                return true
            }
        }
        log.info("Could not run task as either entry or task not found entry={} task={}", eid, taskId)
        return false
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertToConcreteTask(taskId: String, eid: String, def: TaskDefinition): Task<TaskContext> {
        val clazz = Class.forName(def.className)
        if (Task::class.java.isAssignableFrom(clazz)) {
            val instance = clazz.getConstructor(String::class.java, String::class.java).newInstance(taskId, eid) as Task<TaskContext>
            return instance.also(::autowire)
        } else {
            throw IllegalArgumentException("Task must be a subclass of: " + Task::class.qualifiedName)
        }
    }

    private fun autowire(task: Task<TaskContext>) {
        val types = findInjectableTypes(task::class.java)
        task::class.java.methods
            .filter {
                // filter to property setters with @Inject annotation
                it.parameterCount == 1 && it.name.startsWith("set") && types.contains(it.parameterTypes[0])
            }
            .forEach {
                // perform type injection
                val service = serviceProvider.get(it.parameterTypes[0])
                if(service != null) {
                    it.invoke(task, service)
                }
            }
    }

    // find all types where specified class declares a property of that type with the @Inject annotation
    private fun findInjectableTypes(clazz: Class<*>): List<Class<*>> {
        // find all getters with annotation and remove Kotlin generated suffix
        val annotatedMethodNames = clazz.declaredMethods
            .filter { it.isAnnotationPresent( Inject::class.java) }
            .map { it.name.removeSuffix("\$annotations") }.toSet()
        return clazz.declaredMethods
            .filter { annotatedMethodNames.contains(it.name) }
            .map { it.returnType }
    }

    private fun formTaskParams(task: TaskDefinition, params: Map<String, String>): Map<String, String> {
        val taskParams = mutableMapOf<String, String>()
        task.params.forEach {
            if(it.type == TaskParameterType.STATIC) {
                // take static values from stored task definition not user input
                taskParams[it.name] = it.value!!
            } else if(it.required && !params.containsKey(it.name)) {
                throw InvalidModelException("'${it.name}' is required parameter for task")
            }
            else if (it.type == TaskParameterType.ENUM && params.containsKey(it.name) && it.options?.contains(params[it.name]) == false) {
                throw InvalidModelException("Invalid value supplied for param '${it.name}'")
            } else {
                if (params.containsKey(it.name)) {
                    taskParams[it.name] = params.getValue(it.name)
                }
            }
        }
        return taskParams
    }

}
