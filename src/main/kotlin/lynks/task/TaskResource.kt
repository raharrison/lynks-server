package lynks.task

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import lynks.common.exception.InvalidModelException

fun Route.task(taskService: TaskService) {

    fun validateInputParams(raw: Map<*, *>): Map<String, String> {
        val params = mutableMapOf<String, String>()
        raw.entries.forEach {
            if(it.key !is String || it.value !is String) {
                throw InvalidModelException("Invalid task input parameters")
            }
            params[it.key.toString()] = it.value.toString()
        }
        return params
    }

    post("/entry/{entryId}/task/{id}") {
        val entryId = call.parameters["entryId"]!!
        val taskId = call.parameters["id"]!!
        val rawInput = call.receive(Map::class)
        val params = validateInputParams(rawInput)
        if (taskService.runTask(entryId, taskId, params))
            call.respond(HttpStatusCode.OK)
        else
            call.respond(HttpStatusCode.NotFound)
    }

}
