package worker

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import notify.Notification
import notify.NotifyService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import util.JsonMapper.defaultMapper
import kotlin.coroutines.CoroutineContext

abstract class Worker<T>(protected val notifyService: NotifyService) : CoroutineScope {

    protected val log: Logger = LoggerFactory.getLogger(this::class.java)

    var runner: CoroutineContext = Dispatchers.Default
    private val supervisor = SupervisorJob()

    protected open suspend fun beforeWork() {
    }

    protected abstract suspend fun doWork(input: T)

    protected open fun onWorkerFinished(request: T) {}

    protected inline fun launchJob(crossinline job: suspend () -> Unit): Job = launch(coroutineContext) {
        job()
    }

    protected suspend fun sendNotification(notification: Notification = Notification.processed(), body: Any?=null) {
        notifyService.accept(notification, body)
    }

    override val coroutineContext: CoroutineContext
        get() = runner + supervisor
}

abstract class ChannelBasedWorker<T>(notifyService: NotifyService): Worker<T>(notifyService) {

    fun worker(): SendChannel<T> = actor {
        beforeWork()
        for(request in channel) {
            onChannelReceive(request)
        }
    }

    protected open fun onChannelReceive(request: T): Job? {
        return launchJob {
            try {
                doWork(request)
            } finally {
                onWorkerFinished(request)
            }
        }
    }
}

enum class CrudType { CREATE, UPDATE, DELETE }
abstract class VariableWorkerRequest(val crudType: CrudType = CrudType.UPDATE)

abstract class VariableChannelBasedWorker<T : VariableWorkerRequest>(notifyService: NotifyService): ChannelBasedWorker<T>(notifyService) {

    private val jobs = mutableMapOf<T, Job?>()

    private fun launch(request: T) = super.onChannelReceive(request).also {
        jobs[request] = it
    }

    override fun onChannelReceive(request: T): Job? {
        return when(request.crudType) {
            CrudType.CREATE -> launch(request)
            CrudType.UPDATE -> {
                jobs[request]?.cancel()
                jobs.remove(request)
                launch(request)
            }
            CrudType.DELETE -> {
                jobs[request]?.cancel()
                jobs.remove(request)
                null
            }
        }
    }

    override fun onWorkerFinished(request: T) {
        jobs.remove(request)
    }
}

abstract class PersistVariableWorkerRequest(crudType: CrudType = CrudType.UPDATE) : VariableWorkerRequest(crudType) {
    abstract val key: String
}

abstract class PersistedVariableChannelBasedWorker<T : PersistVariableWorkerRequest>(notifyService: NotifyService): VariableChannelBasedWorker<T>(notifyService) {

    private val workerName = javaClass.simpleName
    abstract val requestClass: Class<T>

    override suspend fun beforeWork() {
        super.beforeWork()
        val requests = transaction {
            WorkerSchedules.select { WorkerSchedules.worker eq workerName }.map {
                defaultMapper.readValue(it[WorkerSchedules.request], requestClass)
            }
        }
        requests.forEach {
            onChannelReceive(it)
        }
    }

    override fun onChannelReceive(request: T): Job? {
        deleteSchedule(request) // delete initially
        if(request.crudType != CrudType.DELETE) {
            addSchedule(request) // add back if create/update
        }
        return super.onChannelReceive(request)
    }

    override fun onWorkerFinished(request: T) {
        super.onWorkerFinished(request)
        deleteSchedule(request)
    }

    private fun addSchedule(request: T) = transaction {
        WorkerSchedules.insert {
            it[worker] = workerName
            it[key] = request.key
            it[WorkerSchedules.request] = defaultMapper.writeValueAsString(request)
        }
    }

    protected fun updateSchedule(request: T): Int = transaction {
        WorkerSchedules.update({ (WorkerSchedules.worker eq workerName) and (WorkerSchedules.key eq request.key) }) {
            it[key] = request.key
            it[WorkerSchedules.request] = defaultMapper.writeValueAsString(request)
        }
    }

    private fun deleteSchedule(request: T) = transaction {
        WorkerSchedules.deleteWhere { (WorkerSchedules.worker eq workerName) and (WorkerSchedules.key eq request.key) }
    }

}
