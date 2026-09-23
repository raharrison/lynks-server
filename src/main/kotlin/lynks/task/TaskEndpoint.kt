package lynks.task

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.EntryId
import lynks.common.TaskId
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.util.userId

fun Route.task(taskService: TaskService) {

    fun validateInputParams(raw: Map<*, *>): Map<String, String> {
        val params = mutableMapOf<String, String>()
        raw.entries.forEach {
            if(it.key !is String || (it.value != null && it.value !is String)) {
                throw InvalidModelException("Invalid task input parameter: " + it.key)
            }
            if (it.value != null) {
                params[it.key.toString()] = it.value.toString()
            }
        }
        return params
    }

    post("/entry/{entryId}/task/{id}") {
        val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
        val taskId = TaskId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
        val rawInput = call.receive(Map::class)
        val params = validateInputParams(rawInput)
        if (!taskService.runTask(call.userId(), entryId, taskId, params)) throw NotFoundException()
        call.respond(HttpStatusCode.OK)
    }

}
