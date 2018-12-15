package task

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import resource.Resource
import resource.ResourceManager
import resource.ResourceRetriever
import resource.ResourceType

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
        assertThat(builder.context.input).contains(entry("url", url), entry("name", name))
    }

    @Test
    fun testProcess() {
        val url = "youtube.com"
        val name = "filename"
        val context = resourceRetrievingTask.createContext(mapOf("url" to url, "name" to name))
        val bytes = byteArrayOf(1,2,3,4,5,6)
        coEvery { retriever.getFile(url) } returns bytes
        every { resourceManager.saveUploadedResource("eid", name, any()) } returns
                Resource("rid", "eid", name, "", ResourceType.UPLOAD, 1, 1, 1)

        runBlocking {
            resourceRetrievingTask.process(context)
        }

        coVerify(exactly = 1) { retriever.getFile(url)  }
        verify(exactly = 1) { resourceManager.saveUploadedResource("eid", name, any())  }
    }

    @Test
    fun testProcessNoResult() {
        val url = "youtube.com"
        val context = resourceRetrievingTask.createContext(mapOf("url" to url, "name" to "filename"))
        coEvery { retriever.getFile(url) } returns null

        runBlocking {
            resourceRetrievingTask.process(context)
        }

        coVerify(exactly = 1) { retriever.getFile(url)  }
    }


}