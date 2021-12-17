package lynks.task.youtube

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import lynks.common.Environment
import lynks.common.exception.ExecutionException
import lynks.resource.WebResourceRetriever
import lynks.util.FileUtils
import lynks.util.Result
import org.apache.commons.lang3.SystemUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import java.nio.file.Paths

class YoutubeDlResolverTest {

    private val resourceRetriever = mockk<WebResourceRetriever>()

    @AfterEach
    fun setup() {
        FileUtils.deleteDirectories(listOf(Paths.get(Environment.resource.binaryBasePath)))
    }

    @Test
    fun testRetrieveYoutubeDlBinary() = runBlocking {
        coEvery { resourceRetriever.getFileResult(any()) } returns Result.Success(byteArrayOf(1, 2, 3))

        val resolver = YoutubeDlResolver(resourceRetriever)
        val resolveYoutubeDl = resolver.resolveYoutubeDl()

        assertThat(Path.of(resolveYoutubeDl)).exists()

        coVerify(exactly = 1) { resourceRetriever.getFileResult(any()) }
    }

    @Test
    fun testBinaryAlreadyExists() = runBlocking {
        val binaryName = "youtube-dl${if (SystemUtils.IS_OS_WINDOWS) ".exe" else ""}"
        val binaryPath = Paths.get(Environment.resource.binaryBasePath, binaryName)
        FileUtils.writeToFile(binaryPath, byteArrayOf(1, 2, 3))

        val resolver = YoutubeDlResolver(resourceRetriever)
        val resolveYoutubeDl = resolver.resolveYoutubeDl()

        assertThat(Path.of(resolveYoutubeDl)).exists()

        coVerify(exactly = 0) { resourceRetriever.getFileResult(any()) }
    }

    @Test
    fun testDownloadCallFailed() = runBlocking {
        coEvery { resourceRetriever.getFileResult(any()) } returns Result.Failure(ExecutionException("error"))

        val resolver = YoutubeDlResolver(resourceRetriever)

        assertThrows<ExecutionException> {
            resolver.resolveYoutubeDl()
        }

        coVerify(exactly = 1) { resourceRetriever.getFileResult(any()) }
    }

}
