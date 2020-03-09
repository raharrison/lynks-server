package task

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post

fun Route.task(taskService: TaskService) {

    post("/entry/{entryId}/tasks/{id}") {
        val entryId = call.parameters["entryId"]
        val taskId = call.parameters["id"]
        if(entryId != null && taskId != null) {
            if(taskService.runTask(entryId, taskId))
                call.respond(HttpStatusCode.OK)
            else
                call.respond(HttpStatusCode.BadRequest)
        }
        else
            call.respond(HttpStatusCode.BadRequest)

    }

}
