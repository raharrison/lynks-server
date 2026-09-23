package lynks.common

// Holds only the id, so a deactivated or deleted user is caught when the session is validated
data class UserSession(val userId: String)
