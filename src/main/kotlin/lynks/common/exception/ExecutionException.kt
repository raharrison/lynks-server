package lynks.common.exception

class ExecutionException(message: String, val code: Int = -1) : RuntimeException(message)
