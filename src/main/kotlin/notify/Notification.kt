package notify

private enum class NotificationType { EXECUTED, ERROR, REMINDER }

class Notification private constructor(val type: NotificationType, val message: String = "") {

    companion object {

        fun reminder() = Notification(NotificationType.REMINDER, "Reminder Elapsed")

        fun processed(message: String="Processing Complete") = Notification(NotificationType.EXECUTED, message)

        fun error(message: String ="An Error Occurred") = Notification(NotificationType.ERROR, message)
    }

}