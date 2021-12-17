package lynks.task

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TaskContextTest {

    @Test
    fun testCreation() {
        val input = mapOf("one" to "1", "two" to "2")
        val context = TaskContext(input)
        assertThat(context.input).isEqualTo(input)
    }

    @Test
    fun testEquality() {
        val input = mapOf("one" to "1", "two" to "2")
        val context1 = TaskContext(input)
        val context2 = TaskContext(input)
        assertThat(context1).isEqualTo(context1)
        assertThat(context1).isEqualTo(context2)

        assertThat(context1.hashCode()).isEqualTo(context1.hashCode())
        assertThat(context1.hashCode()).isEqualTo(context2.hashCode())
    }
}
