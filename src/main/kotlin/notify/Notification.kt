package notify

private enum class NotificationType { EXECUTED, ERROR, REMINDER }

class Notification private constructor(val type: NotificationType, val message: String = "") {

    companion object {

        fun reminder() = Notification(NotificationType.REMINDER, "Reminder Elapsed")

    }

}