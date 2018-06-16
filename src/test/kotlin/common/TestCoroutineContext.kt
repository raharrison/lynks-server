package common

import kotlinx.coroutines.experimental.CancellableContinuation
import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlinx.coroutines.experimental.Delay
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.CoroutineContext

// delay calls will be run immediately
class TestCoroutineContext : CoroutineDispatcher(), Delay {
    override fun scheduleResumeAfterDelay(time: Long, unit: TimeUnit, continuation: CancellableContinuation<Unit>) {
        continuation.resume(Unit)
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        //CommonPool.dispatch(context, block)  // dispatch on CommonPool
        block.run()  // dispatch on calling thread
    }
}