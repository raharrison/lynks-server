package lynks.common

interface IdBasedNewEntity {
    val id: String?
}

interface IdBasedCreatedEntity {
    val id: String  // Keep as String for generic entities (Groups, etc.)
}

interface TypedIdEntity<T> {
    val id: T
}

interface NewTypedIdEntity<T> {
    val id: T?
}
