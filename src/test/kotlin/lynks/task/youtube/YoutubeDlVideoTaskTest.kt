package lynks.task.youtube

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import lynks.common.Link
import lynks.common.exception.InvalidModelException
import lynks.entry.LinkService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class YoutubeDlVideoTaskTest {

    private val linkService = mockk<LinkService>()
    private val youtubeDlRunner = mockk<YoutubeDlRunner>(relaxUnitFun = true)

    private val youtubeDlTask = YoutubeDlVideoTask("tid", "eid").also {
        it.linkService = linkService
        it.youtubeDlRunner = youtubeDlRunner
    }

    private val link = Link("eid", "title", "youtube.com/watch?v=1234", "src", "", 123L, 123L)

    @Test
    fun testContextConstruct() {
        val type = YoutubeDlVideoTask.YoutubeDlVideoType.BEST_VIDEO_TRANSCODE
        val sponsorBlock = SponsorBlockOptions.MARK_CHAPTERS
        val params = mutableMapOf("type" to type.name, "startTime" to "00:10:30", "endTime" to "01:35:54", "sponsorBlock" to sponsorBlock.name)
        val context = youtubeDlTask.createContext(params)
        assertThat(context.type).isEqualTo(type)
        assertThat(context.startTime).isEqualTo("00:10:30")
        assertThat(context.endTime).isEqualTo("01:35:54")
        assertThat(context.sponsorBlock).isEqualTo(sponsorBlock)

        params["endTime"] = "4f:ii:22"
        assertThrows<InvalidModelException> {
            youtubeDlTask.createContext(params)
        }
    }

    @Test
    fun testBuilder() {
        val builder = YoutubeDlVideoTask.build()
        assertThat(builder.clazz).isEqualTo(YoutubeDlVideoTask::class)
        assertThat(builder.params).extracting("name").containsOnly("type", "startTime", "endTime", "sponsorBlock")
    }

    @Test
    fun testProcessLink() {
        val type = YoutubeDlVideoTask.YoutubeDlVideoType.BEST_VIDEO
        val sponsorBlock = SponsorBlockOptions.MARK_CHAPTERS
        val params = mapOf("type" to type.name, "startTime" to "00:10:30", "endTime" to "01:35:54", "sponsorBlock" to sponsorBlock.name)
        val context = youtubeDlTask.createContext(params)

        every { linkService.get(link.id) } returns link

        runBlocking {
            youtubeDlTask.process(context)
        }

        verify(exactly = 1) { linkService.get(link.id) }
        coVerify(exactly = 1) { youtubeDlRunner.run(link.id, link.url, any(), any()) }
    }

}
