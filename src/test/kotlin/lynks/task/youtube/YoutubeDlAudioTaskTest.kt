package lynks.task.youtube

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import lynks.common.Link
import lynks.entry.LinkService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class YoutubeDlAudioTaskTest {

    private val linkService = mockk<LinkService>()
    private val youtubeDlRunner = mockk<YoutubeDlRunner>(relaxUnitFun = true)

    private val youtubeDlTask = YoutubeDlAudioTask("tid", "eid").also {
        it.linkService = linkService
        it.youtubeDlRunner = youtubeDlRunner
    }

    private val link = Link("eid", "title", "youtube.com/watch?v=1234", "src", "", 123L, 123L)

    @Test
    fun testContextConstruct() {
        val type = YoutubeDlAudioTask.YoutubeDlAudioType.BEST_AUDIO
        val context = youtubeDlTask.createContext(mapOf("type" to type.name))
        assertThat(context.type).isEqualTo(type)
    }

    @Test
    fun testBuilder() {
        val builder = YoutubeDlAudioTask.build()
        assertThat(builder.clazz).isEqualTo(YoutubeDlAudioTask::class)
        assertThat(builder.params).extracting("name").containsOnly("type")
    }

    @Test
    fun testProcessLink() {
        val type = YoutubeDlAudioTask.YoutubeDlAudioType.BEST_AUDIO
        val context = youtubeDlTask.createContext(mapOf("type" to type.name))

        every { linkService.get(link.id) } returns link

        runBlocking {
            youtubeDlTask.process(context)
        }

        verify(exactly = 1) { linkService.get(link.id) }
        coVerify(exactly = 1) { youtubeDlRunner.run(link.id, link.url, any(), any()) }
    }

}
