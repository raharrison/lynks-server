package lynks.user

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.ConfigMode
import lynks.common.Environment
import lynks.common.UserSession
import lynks.util.isCallAuthorizedForUser

fun Route.twoFactor(twoFactorService: TwoFactorService) {


    route("/user/2fa") {

        get {
            val username = call.principal<UserSession>()?.username ?: if (Environment.mode != ConfigMode.PROD) {
                Environment.auth.defaultUserName
            } else {
                return@get call.respond(UnauthorizedResponse())
            }
            if (call.isCallAuthorizedForUser(username)) {
                val enabled = twoFactorService.getTwoFactorSecret(username) != null
                call.respond(HttpStatusCode.OK, mapOf("enabled" to enabled))
            } else {
                call.respond(UnauthorizedResponse())
            }
        }

        get("/secret") {
            val username = call.principal<UserSession>()?.username ?: if (Environment.mode != ConfigMode.PROD) {
                Environment.auth.defaultUserName
            } else {
                return@get call.respond(UnauthorizedResponse())
            }
            if (call.isCallAuthorizedForUser(username)) {
                val secret = twoFactorService.getTwoFactorSecret(username) ?: ""
                // convert to QR code
                call.respond(HttpStatusCode.OK, mapOf("secret" to secret))
            } else {
                call.respond(UnauthorizedResponse())
            }
        }

        post("/validate") {
            val username = call.principal<UserSession>()?.username ?: if (Environment.mode != ConfigMode.PROD) {
                Environment.auth.defaultUserName
            } else {
                return@post call.respond(UnauthorizedResponse())
            }
            val request = call.receive<TwoFactorValidateRequest>()
            if (call.isCallAuthorizedForUser(username)) {
                val valid = twoFactorService.validateTotp(username, request.code) == AuthResult.SUCCESS
                call.respond(HttpStatusCode.OK, mapOf("valid" to valid))
            } else {
                call.respond(UnauthorizedResponse())
            }
        }

        put {
            val username = call.principal<UserSession>()?.username ?: if (Environment.mode != ConfigMode.PROD) {
                Environment.auth.defaultUserName
            } else {
                return@put call.respond(UnauthorizedResponse())
            }
            val request = call.receive<TwoFactorUpdateRequest>()
            if (call.isCallAuthorizedForUser(username)) {
                val updated = twoFactorService.updateTwoFactorEnabled(username, request.enabled)
                if (!updated) call.respond(UnauthorizedResponse())
                else call.respond(HttpStatusCode.OK)
            } else {
                call.respond(UnauthorizedResponse())
            }
        }

    }
}
