package lynks.task

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import lynks.common.exception.ExecutionException
import lynks.resource.Resource
import lynks.resource.ResourceManager
import lynks.resource.ResourceRetriever
import lynks.resource.ResourceType
import lynks.util.Result
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResourceRetrievingTaskTest {

    private val resourceManager = mockk<ResourceManager>()
    private val retriever = mockk<ResourceRetriever>()

    private val resourceRetrievingTask = ResourceRetrievingTask("tid", "eid").also {
        it.resourceManager = resourceManager
        it.resourceRetriever = retriever
    }

    @Test
    fun testContextConstruct() {
        val url = "youtube.com"
        val name = "filename"
        val context = resourceRetrievingTask.createContext(mapOf("url" to url, "name" to name))
        assertThat(context.name).isEqualTo(name)
        assertThat(context.url).isEqualTo(url)
    }

    @Test
    fun testBuilder() {
        val url = "youtube.com"
        val name = "filename"
        val builder = ResourceRetrievingTask.build(url, name)
        assertThat(builder.clazz).isEqualTo(ResourceRetrievingTask::class)
        assertThat(builder.params).hasSize(2).extracting("name").containsOnly("url", "name")
    }

    @Test
    fun testProcess() {
        val url = "youtube.com"
        val name = "filename"
        val context = resourceRetrievingTask.createContext(mapOf("url" to url, "name" to name))
        val bytes = byteArrayOf(1,2,3,4,5,6)
        coEvery { retriever.getFileResult(url) } returns Result.Success(bytes)
        every { resourceManager.saveUploadedResource("eid", name, any()) } returns
                Resource("rid", "eid", name, "", ResourceType.UPLOAD, 1, 1, 1)

        runBlocking {
            resourceRetrievingTask.process(context)
        }

        coVerify(exactly = 1) { retriever.getFileResult(url)  }
        coVerify(exactly = 1) { resourceManager.saveUploadedResource("eid", name, any())  }
    }

    @Test
    fun testProcessNoResult() {
        val url = "youtube.com"
        val context = resourceRetrievingTask.createContext(mapOf("url" to url, "name" to "filename"))
        coEvery { retriever.getFileResult(url) } returns Result.Failure(ExecutionException("error"))

        runBlocking {
            resourceRetrievingTask.process(context)
        }

        coVerify(exactly = 1) { retriever.getFileResult(url)  }
        coVerify(exactly = 0) { resourceManager.saveUploadedResource("eid", any(), any())  }
    }


}
