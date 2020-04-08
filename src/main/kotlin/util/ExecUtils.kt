package util

import common.exception.ExecutionException
import org.apache.commons.lang3.SystemUtils
import java.io.InputStream
import java.util.*

object ExecUtils {

    private val log = loggerFor<ExecUtils>()

    private val NOOP_CONSUMER: (line: String) -> Unit = {}

    private val startupCommands = generateStartupCommands()

    private fun generateStartupCommands(): List<String> =
            if (SystemUtils.IS_OS_WINDOWS)
                listOf("cmd.exe", "/C")
            else
                listOf("sh", "-c")

    fun executeCommand(command: String, env: HashMap<String, String> = hashMapOf(), listener: (String) -> Unit = NOOP_CONSUMER): Result<String, ExecutionException> {
        return execLocal(command, env, listener)
    }

    private fun execLocal(command: String, env: HashMap<String, String>, listener: (String) -> Unit): Result<String, ExecutionException> {
        val commands = startupCommands.toMutableList()
        commands.add(command)

        val pb = ProcessBuilder(commands)
        pb.environment().putAll(env)
        log.info("Running: ${pb.command()} \n with env $env")

        val process = pb.start()
        val result = collectOutput(process.inputStream, listener)
        log.debug("Collected {} lines of output from command execution", result.length)
        val errors = process.errorStream.bufferedReader().use { it.readText() }
        process.outputStream.close()

        val res = process.waitFor()
        process.destroy()
        if (res != 0) {
            log.info("Failed to execute command {}.\nstderr: {}\nstdout: {}", pb.command(), errors, result)
            return Result.Failure(ExecutionException(errors, res))
        }
        log.info("Command executed successfully")
        return Result.Success(result)
    }

    private fun collectOutput(inputStream: InputStream, listener: (String) -> Unit): String {
        val out = StringBuilder()
        inputStream.bufferedReader().use {
            var line: String? = it.readLine()
            do {
                if (line != null) {
                    out.append(line).append("\n")
                    listener(line)
                }
                line = it.readLine()
            } while (line != null)
            return out.toString()
        }
    }

}