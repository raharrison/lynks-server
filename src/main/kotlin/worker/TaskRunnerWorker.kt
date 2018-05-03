package worker

import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.launch
import task.Task
import task.TaskContext

class TaskRunnerRequest(val task: Task<TaskContext>, val context: TaskContext)

class TaskRunnerWorker: Worker {

    override fun worker() = actor<TaskRunnerRequest> {
        for(request in channel) {
            launch {
                request.task.process(request.context)
            }
        }
    }

}