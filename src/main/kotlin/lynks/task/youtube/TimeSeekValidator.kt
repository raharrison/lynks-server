package lynks.task.youtube

import lynks.common.exception.InvalidModelException

object TimeSeekValidator {

    private val seekFormat = Regex("\\d{2}:\\d{2}:\\d{2}")

    fun validateStartTime(startTime: String?) {
        if (startTime != null) {
            if (!startTime.matches(seekFormat)) {
                throw InvalidModelException("Invalid start time, expected format '00:00:00'")
            }
        }
    }

    fun validateEndTime(endTime: String?) {
        if (endTime != null) {
            if (!endTime.matches(seekFormat)) {
                throw InvalidModelException("Invalid end time, expected format '00:00:00'")
            }
        }
    }

}
