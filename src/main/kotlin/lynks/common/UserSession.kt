package lynks.common

import io.ktor.auth.*

data class UserSession(val username: String) : Principal
