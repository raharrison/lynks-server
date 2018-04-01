package worker

import kotlinx.coroutines.experimental.channels.SendChannel

interface Worker {

    fun worker(): SendChannel<*>

}