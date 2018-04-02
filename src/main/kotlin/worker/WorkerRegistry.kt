package worker

import link.LinkProcessingRequest
import link.LinkProcessorWorker
import resource.ResourceManager

class WorkerRegistry(resourceManager: ResourceManager) {

    private val linkWorker = LinkProcessorWorker(resourceManager).worker()

    fun acceptLinkWork(request: LinkProcessingRequest) {
        linkWorker.offer(request)
    }

    private fun startScheduledWorkers() {
        TempFileCleanupWorker().run()
    }
}