package lynks.common

import com.fasterxml.jackson.annotation.JsonValue
import lynks.util.RandomUtils

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

@JvmInline
value class DigestId(@JsonValue val value: String) {
    override fun toString(): String = value
}

fun newEntryId(): EntryId = EntryId(RandomUtils.generateUid())
fun newResourceId(): ResourceId = ResourceId(RandomUtils.generateUid())
fun newTaskId(): TaskId = TaskId(RandomUtils.generateUid())
fun newNotificationId(): NotificationId = NotificationId(RandomUtils.generateUid())
fun newReminderId(): ReminderId = ReminderId(RandomUtils.generateUid())
fun newCommentId(): CommentId = CommentId(RandomUtils.generateUid())
fun newDigestId(): DigestId = DigestId(RandomUtils.generateUid())
