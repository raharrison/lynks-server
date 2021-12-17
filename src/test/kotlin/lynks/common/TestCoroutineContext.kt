package lynks.common

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

// delay calls will be run immediately
@OptIn(InternalCoroutinesApi::class)
class TestCoroutineContext : CoroutineDispatcher(), Delay {

    override fun dispatchYield(context: CoroutineContext, block: Runnable) {
        block.run()
    }

    override suspend fun delay(time: Long) {
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        continuation.resume(Unit)
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        //CommonPool.dispatch(context, block)  // dispatch on CommonPool
        block.run()  // dispatch on calling thread
    }
}
