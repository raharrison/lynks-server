package lynks.worker

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import lynks.util.JsonMapper.defaultMapper
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

abstract class Worker<T> : CoroutineScope {

    protected val log: Logger = LoggerFactory.getLogger(this::class.java)

    var runner: CoroutineContext = Dispatchers.Default
    protected val supervisor = SupervisorJob()

    protected open suspend fun beforeWork() {
    }

    protected abstract suspend fun doWork(input: T)

    protected open fun onWorkerFinished(request: T) {}

    protected inline fun launchJob(crossinline job: suspend () -> Unit): Job = launch(coroutineContext) {
        job()
    }

    override val coroutineContext: CoroutineContext
        get() = runner + supervisor
}

abstract class ChannelBasedWorker<T> : Worker<T>() {

    @OptIn(ObsoleteCoroutinesApi::class)
    // Callers use trySend, which a rendezvous channel rejects whenever the actor is busy
    fun worker(): SendChannel<T> = actor(capacity = Channel.UNLIMITED) {
        beforeWork()
        for (request in channel) {
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

abstract class VariableChannelBasedWorker<T : VariableWorkerRequest> : ChannelBasedWorker<T>() {

    private val jobs = ConcurrentHashMap<T, Job>()

    private fun launch(request: T) = super.onChannelReceive(request)?.also {
        jobs[request] = it
    }

    override fun onChannelReceive(request: T): Job? {
        return when (request.crudType) {
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

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        supervisor.cancel()
    }

    override fun onWorkerFinished(request: T) {
        // only remove if it exactly matches the original request not via .equals
        jobs.keys.find { it === request }?.let {
            jobs.remove(request)
        }
    }
}

abstract class PersistVariableWorkerRequest(crudType: CrudType = CrudType.UPDATE) : VariableWorkerRequest(crudType) {
    abstract val key: String
}

abstract class PersistedVariableChannelBasedWorker<T : PersistVariableWorkerRequest> :
    VariableChannelBasedWorker<T>() {

    private val workerName = javaClass.simpleName
    abstract val requestClass: Class<T>

    override suspend fun beforeWork() {
        super.beforeWork()
        val requests = transaction {
            WorkerSchedules.selectAll().where { WorkerSchedules.worker eq workerName }.map {
                defaultMapper.readValue(it[WorkerSchedules.request], requestClass)
            }
        }
        requests.forEach {
            onChannelReceive(it)
        }
    }

    override fun onChannelReceive(request: T): Job? {
        val lastRun = getLastRunTime(request)
        deleteSchedule(request) // delete initially
        if (request.crudType != CrudType.DELETE) {
            addSchedule(request, lastRun) // add back if create/update
        }
        return super.onChannelReceive(request)
    }

    override fun onWorkerFinished(request: T) {
        super.onWorkerFinished(request)
        deleteSchedule(request)
    }

    private fun addSchedule(request: T, lastRun: OffsetDateTime?) = transaction {
        WorkerSchedules.insert {
            it[worker] = workerName
            it[key] = request.key
            it[WorkerSchedules.request] = defaultMapper.writeValueAsString(request)
            it[WorkerSchedules.lastRun] = lastRun
        }
    }

    protected fun updateSchedule(request: T): Int = transaction {
        WorkerSchedules.update({ (WorkerSchedules.worker eq workerName) and (WorkerSchedules.key eq request.key) }) {
            it[key] = request.key
            it[WorkerSchedules.request] = defaultMapper.writeValueAsString(request)
            it[lastRun] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    protected fun getLastRunTime(request: T) = transaction {
        WorkerSchedules.select(WorkerSchedules.lastRun)
            .where { (WorkerSchedules.worker eq workerName) and (WorkerSchedules.key eq request.key) }
            .map { it[WorkerSchedules.lastRun] }
            .singleOrNull()
    }

    private fun deleteSchedule(request: T) = transaction {
        WorkerSchedules.deleteWhere { (WorkerSchedules.worker eq workerName) and (WorkerSchedules.key eq request.key) }
    }

}
