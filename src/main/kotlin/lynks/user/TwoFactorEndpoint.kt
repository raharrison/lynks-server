package lynks.user

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.exception.UnauthorizedException
import lynks.util.userId

fun Route.twoFactor(twoFactorService: TwoFactorService) {

    route("/user/2fa") {

        get {
            val enabled = twoFactorService.getTwoFactorSecret(call.userId()) != null
            call.respond(HttpStatusCode.OK, mapOf("enabled" to enabled))
        }

        get("/secret") {
            val secret = twoFactorService.getTwoFactorSecret(call.userId()) ?: ""
            call.respond(HttpStatusCode.OK, mapOf("secret" to secret))
        }

        post("/validate") {
            val request = call.receive<TwoFactorValidateRequest>()
            val valid = twoFactorService.validateTotp(call.userId(), request.code) == AuthResult.SUCCESS
            call.respond(HttpStatusCode.OK, mapOf("valid" to valid))
        }

        put {
            val request = call.receive<TwoFactorUpdateRequest>()
            if (!twoFactorService.updateTwoFactorEnabled(call.userId(), request.enabled)) throw UnauthorizedException()
            call.respond(HttpStatusCode.OK)
        }

    }
}
