package worker

import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.launch
import notify.Notification
import notify.NotifyService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import util.JsonMapper.defaultMapper
import kotlin.coroutines.experimental.CoroutineContext

abstract class Worker<T>(protected val notifyService: NotifyService) {

    var runner: CoroutineContext = DefaultDispatcher

    protected open suspend fun beforeWork() {
    }

    protected abstract suspend fun doWork(input: T)

    protected open fun onWorkerFinished(request: T) {}

    protected inline fun launchJob(crossinline job: suspend () -> Unit): Job = launch(runner) {
        job()
    }

    protected suspend fun sendNotification(notification: Notification = Notification.processed(), body: Any?=null) {
        notifyService.accept(notification, body)
    }
}

abstract class ChannelBasedWorker<T>(notifyService: NotifyService): Worker<T>(notifyService) {

    fun worker(): SendChannel<T> = actor(runner) {
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
                launch(request)
            }
            CrudType.DELETE -> {
                jobs[request]?.cancel()
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
        transaction {
            WorkerSchedules.select { WorkerSchedules.worker eq workerName }.map {
                val request: T = defaultMapper.readValue(it[WorkerSchedules.request], requestClass)
                onChannelReceive(request)
            }
        }
    }

    override fun onChannelReceive(request: T): Job? {
        return super.onChannelReceive(request).also {
            deleteSchedule(request) // delete initially
            if(request.crudType != CrudType.DELETE) {
                addSchedule(request) // add back if create/update
            }
        }
    }

    override fun onWorkerFinished(request: T) {
        super.onWorkerFinished(request)
        deleteSchedule(request)
    }

    private fun addSchedule(request: T) = transaction {
        WorkerSchedules.insert {
            it[WorkerSchedules.worker] = workerName
            it[WorkerSchedules.key] = request.key
            it[WorkerSchedules.request] = defaultMapper.writeValueAsString(request)
        }
    }

    private fun updateSchedule(request: T): Int = transaction {
        WorkerSchedules.update({ (WorkerSchedules.worker eq workerName) and (WorkerSchedules.key eq request.key) }) {
            it[WorkerSchedules.key] = request.key
            it[WorkerSchedules.request] = defaultMapper.writeValueAsString(request)
        }
    }

    private fun deleteSchedule(request: T) = transaction {
        WorkerSchedules.deleteWhere { (WorkerSchedules.worker eq workerName) and (WorkerSchedules.key eq request.key) }
    }

}
