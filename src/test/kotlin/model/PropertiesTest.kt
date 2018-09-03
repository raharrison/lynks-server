package model

import common.BaseProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import task.Task
import task.TaskBuilder
import task.TaskContext

class PropertiesTest {

    @Test
    fun testAddTask() {
        val props = BaseProperties()
        val builder = TaskBuilder(Task::class, TaskContext())
        val def = props.addTask("desc", builder)
        val task = props.getTask(def.id)
        assertThat(task?.input).isEmpty()
        assertThat(task?.description).isEqualTo("desc")
        assertThat(task?.className).isEqualTo(Task::class.qualifiedName)
    }

    @Test
    fun testGetNoTask() {
        val props = BaseProperties()
        props.addTask("desc", TaskBuilder(Task::class, TaskContext()))
        val task = props.getTask("invalid")
        assertThat(task).isNull()
    }

    @Test
    fun testAddDuplicateTestOverwrites() {
        val props = BaseProperties()
        val def = props.addTask("desc", TaskBuilder(Task::class, TaskContext()))
        val task = props.getTask(def.id)
        assertThat(task).isNotNull

        val builder = TaskBuilder(Task::class, TaskContext(mapOf("1" to "one")))
        val def2 = props.addTask("desc", builder)
        val task2 = props.getTask(def2.id)
        assertThat(task2?.description).isEqualTo("desc")
        assertThat(task2?.input).hasSize(1).isEqualTo(builder.context.input)
    }
}