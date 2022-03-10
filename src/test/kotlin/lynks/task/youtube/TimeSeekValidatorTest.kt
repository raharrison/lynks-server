package lynks.task.youtube

import lynks.common.exception.InvalidModelException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TimeSeekValidatorTest {

    @Test
    fun testValidateStartTime() {
        TimeSeekValidator.validateStartTime("03:46:32")
        assertThrows<InvalidModelException> {
            TimeSeekValidator.validateStartTime("4f:ii:22")
        }
    }

    @Test
    fun testValidateEndTime() {
        TimeSeekValidator.validateEndTime("03:46:32")
        assertThrows<InvalidModelException> {
            TimeSeekValidator.validateEndTime("4f:ii:22")
        }
    }
}
