package util

import com.github.kittinunf.result.Result
import org.apache.commons.lang3.SystemUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class ExecUtilsTest {

    @Test
    fun testEchoCommand() {
        val expected = "something"
        val result = ExecUtils.executeCommand("echo $expected")
        when(result) {
            is Result.Success -> assertThat(result.value.trim()).isEqualTo(expected)
            else -> fail("Command execution failed")
        }
    }

    @Test
    fun testInvalidCommand() {
        val command = "gduiohhsj"
        val result = ExecUtils.executeCommand(command)
        when(result) {
            is Result.Failure -> {
                assertThat(result.getException().code).isNotEqualTo(0)
            }
            else -> fail("Command execution didn't fail")
        }
    }

    @Test
    fun testEnvVariablesSet() {
        val key = "value"
        val value = "something"
        val command = if(SystemUtils.IS_OS_WINDOWS) "echo %$key%" else "echo $$key"
        val result = ExecUtils.executeCommand(command, env = hashMapOf(key to value))
        when(result) {
            is Result.Success -> assertThat(result.value.trim()).isEqualTo(value)
            else -> fail("Command execution failed")
        }
    }

    @Test
    fun testCommandListener() {
        val expected = "something"
        val result = ExecUtils.executeCommand("echo $expected", listener = {
            assertThat(it).isEqualTo(expected)
        })
        when(result) {
            is Result.Success -> assertThat(result.value.trim()).isEqualTo(expected)
            else -> fail("Command execution failed")
        }
    }

}