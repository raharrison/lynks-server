package util

import com.github.kittinunf.result.Result
import org.apache.commons.lang3.SystemUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStream
import java.util.*

class ExecException(val code: Int, message: String) : RuntimeException(message)

object ExecUtils {

    private val LOGGER: Logger = LoggerFactory.getLogger(ExecUtils::class.java)

    private val NOOP_CONSUMER: (line: String) -> Unit = {}

    private val startupCommands = generateStartupCommands()

    private fun generateStartupCommands(): List<String> =
            if (SystemUtils.IS_OS_WINDOWS)
                listOf("cmd.exe", "/C")
            else
                listOf("sh", "-c")

    fun executeCommand(command: String, env: HashMap<String, String> = hashMapOf(), listener: (String) -> Unit = NOOP_CONSUMER): Result<String, ExecException> {
        return execLocal(command, env, listener)
    }

    private fun execLocal(command: String, env: HashMap<String, String>, listener: (String) -> Unit): Result<String, ExecException> {
        val commands = startupCommands.toMutableList()
        commands.add(command)

        val pb = ProcessBuilder(commands)
        pb.environment().putAll(env)
        LOGGER.info("Running: ${pb.command()} \n with env $env")

        val process = pb.start()
        val result = collectOutput(process.inputStream, listener)
        val errors = process.errorStream.bufferedReader().use { it.readText() }

        val res = process.waitFor()
        if (res != 0) {
            LOGGER.info("Failed to execute command {}.\nstderr: {}\nstdout: {}", pb.command(), errors, result)
            return Result.Failure(ExecException(res, errors))
        }
        return Result.Success(result)
    }

    private fun collectOutput(inputStream: InputStream, listener: (String) -> Unit): String {
        val out = StringBuilder()
        val buf: BufferedReader = inputStream.bufferedReader()
        var line: String? = buf.readLine()
        do {
            if (line != null) {
                out.append(line).append("\n")
                listener(line)
            }
            line = buf.readLine()
        } while (line != null)
        return out.toString()
    }

}