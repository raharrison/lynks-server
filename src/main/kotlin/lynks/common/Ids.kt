package lynks.common

import com.fasterxml.jackson.annotation.JsonValue

@JvmInline
value class EntryId(@JsonValue val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class ResourceId(@JsonValue val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class TaskId(@JsonValue val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class NotificationId(@JsonValue val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class ReminderId(@JsonValue val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class CommentId(@JsonValue val value: String) {
    override fun toString(): String = value
}
