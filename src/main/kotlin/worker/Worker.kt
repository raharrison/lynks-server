package worker

import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import java.util.concurrent.TimeUnit

interface Worker {

    fun worker(): SendChannel<*>

}

abstract class ScheduledWorker(private val time: Long, private val unit: TimeUnit = TimeUnit.MILLISECONDS) {

    fun run() {
        launch {
            doWork()
            delay(time, unit)
        }
    }

    protected abstract fun doWork()

}