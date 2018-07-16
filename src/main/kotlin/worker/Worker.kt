package worker

import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import notify.Notification
import notify.NotifyService
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.CoroutineContext

private val workerContext = DefaultDispatcher

abstract class Worker<T>(private val notifyService: NotifyService) {

    var runner: CoroutineContext = workerContext

    fun worker(): SendChannel<T> = actor(runner) {
        beforeWork()
        for(request in channel) {
            launch(runner) {
                doWork(request)
            }
        }
    }

    protected open suspend fun beforeWork() {
    }

    protected abstract suspend fun doWork(input: T)

    protected fun launchJob(job: suspend () -> Unit) = launch(runner) {
        job()
    }

    protected suspend fun sendNotification(notification: Notification = Notification.processed(), body: Any?=null) {
        notifyService.accept(notification, body)
    }
}

abstract class ScheduledWorker(private val time: Long, private val unit: TimeUnit = TimeUnit.MILLISECONDS) {

    fun run() {
        launch(workerContext) {
            while(true) {
                try {
                    doWork()
                } catch (e: Exception) {
                    // log error
                }
                finally {
                    delay(time, unit)
                }
            }
        }
    }

    protected abstract fun doWork()

}