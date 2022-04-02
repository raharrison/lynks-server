package lynks.worker

import lynks.task.Task
import lynks.task.TaskContext

class TaskRunnerRequest(val task: Task<TaskContext>, val context: TaskContext)

class TaskRunnerWorker : ChannelBasedWorker<TaskRunnerRequest>() {

    override suspend fun doWork(input: TaskRunnerRequest) {
        input.task.process(input.context)
    }

}
