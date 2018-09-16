package worker

import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.launch
import notify.Notification
import notify.NotifyService
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
abstract class VariableWorkerRequest(val crudType: CrudType)

abstract class VariableChannelBasedWorker<T : VariableWorkerRequest>(notifyService: NotifyService): ChannelBasedWorker<T>(notifyService) {

    protected val jobs = mutableMapOf<T, Job?>()

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
