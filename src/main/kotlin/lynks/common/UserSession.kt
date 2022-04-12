package lynks.common

import io.ktor.server.auth.*

data class UserSession(val username: String) : Principal
