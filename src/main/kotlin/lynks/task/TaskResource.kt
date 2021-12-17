package lynks.task

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

fun Route.task(taskService: TaskService) {

    post("/entry/{entryId}/task/{id}") {
        val entryId = call.parameters["entryId"]!!
        val taskId = call.parameters["id"]!!
        if (taskService.runTask(entryId, taskId))
            call.respond(HttpStatusCode.OK)
        else
            call.respond(HttpStatusCode.NotFound)
    }

}
