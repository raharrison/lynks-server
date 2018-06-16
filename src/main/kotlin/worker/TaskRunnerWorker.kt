package worker

import task.Task
import task.TaskContext

class TaskRunnerRequest(val task: Task<TaskContext>, val context: TaskContext)

class TaskRunnerWorker: Worker<TaskRunnerRequest>() {

    override suspend fun doWork(input: TaskRunnerRequest) {
        input.task.process(input.context)
    }

}