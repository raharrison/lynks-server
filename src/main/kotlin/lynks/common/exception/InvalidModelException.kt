package lynks.common.exception

class InvalidModelException : RuntimeException {
    constructor() : super()
    constructor(message: String) : super(message)
}
