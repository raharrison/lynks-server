package notify

internal enum class NotificationType { EXECUTED, ERROR, REMINDER, DISCUSSIONS }

enum class NotificationMethod { EMAIL, PUSH, BOTH }

class Notification private constructor(internal val type: NotificationType, val message: String = "") {

    companion object {

        fun reminder(message: String = "Reminder Elapsed") = Notification(NotificationType.REMINDER, message)

        fun processed(message: String = "Processing Complete") = Notification(NotificationType.EXECUTED, message)

        fun error(message: String = "An Error Occurred") = Notification(NotificationType.ERROR, message)

        fun discussions(message: String = "Discussions Found") = Notification(NotificationType.DISCUSSIONS, message)
    }

}