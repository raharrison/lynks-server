package lynks.model

import lynks.common.BaseProperties
import lynks.common.TaskParameter
import lynks.common.TaskParameterType
import lynks.task.Task
import lynks.task.TaskBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PropertiesTest {

    @Test
    fun testAddTask() {
        val props = BaseProperties()
        val builder = TaskBuilder(Task::class)
        val def = props.addTask("desc", builder)
        val task = props.getTask(def.id)
        assertThat(task?.params).isEmpty()
        assertThat(task?.description).isEqualTo("desc")
        assertThat(task?.className).isEqualTo(Task::class.qualifiedName)
    }

    @Test
    fun testGetNoTask() {
        val props = BaseProperties()
        props.addTask("desc", TaskBuilder(Task::class))
        val task = props.getTask("invalid")
        assertThat(task).isNull()
    }

    @Test
    fun testClearTasks() {
        val props = BaseProperties()
        val def1 = props.addTask("desc1", TaskBuilder(Task::class))
        assertThat(props.getTask(def1.id)).isNotNull
        props.clearTasks()
        val def2 = props.addTask("desc2", TaskBuilder(Task::class))
        assertThat(props.getTask(def1.id)).isNull()
        assertThat(props.getTask(def2.id)).isNotNull
    }

    @Test
    fun testAddDuplicateTaskOverwrites() {
        val props = BaseProperties()
        val def = props.addTask("desc", TaskBuilder(Task::class))
        val task = props.getTask(def.id)
        assertThat(task).isNotNull

        val builder = TaskBuilder(Task::class, listOf(TaskParameter("p1", TaskParameterType.TEXT)))
        val def2 = props.addTask("desc", builder)
        val task2 = props.getTask(def2.id)
        assertThat(task2?.description).isEqualTo("desc")
        assertThat(task2?.params).hasSize(1).containsExactlyElementsOf(builder.params)
    }

    @Test
    fun testAttributes() {
        val props = BaseProperties()
        assertThat(props.getAttribute("invalid")).isNull()

        props.addAttribute("attr", "val")
        assertThat(props.containsAttribute("attr")).isTrue()
        assertThat(props.getAttribute("attr")).isEqualTo("val")

        props.removeAttribute("attr")
        assertThat(props.containsAttribute("attr")).isFalse()
        assertThat(props.getAttribute("attr")).isNull()
    }

    @Test
    fun testMerge() {
        val props1 = BaseProperties()
        props1.addAttribute("attr1", "val1")
        props1.addAttribute("attr2", "val2")
        val t1 = props1.addTask("desc1", TaskBuilder(Task::class))

        val props2 = BaseProperties()
        props2.addAttribute("attr2", "updated")
        props2.addAttribute("attr3", "val3")
        val t2 = props2.addTask("desc2", TaskBuilder(Task::class))

        val merged = props1.merge(props2)
        assertThat(merged.getAttribute("attr1")).isEqualTo("val1")
        assertThat(merged.getAttribute("attr2")).isEqualTo("updated")
        assertThat(merged.getAttribute("attr3")).isEqualTo("val3")

        assertThat(merged.attributes).hasSize(3)
        assertThat(merged.tasks).hasSize(2)

        assertThat(merged.getTask(t1.id)?.description).isEqualTo("desc1")
        assertThat(merged.getTask(t2.id)?.description).isEqualTo("desc2")
    }
}
