package service

import common.DatabaseTest
import common.EntryType
import common.NewLink
import common.PageRequest
import entry.LinkService
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import resource.ResourceManager
import tag.TagService
import worker.PersistLinkProcessingRequest
import worker.WorkerRegistry
import java.sql.SQLException

class LinkServiceTest : DatabaseTest() {

    private val tagService = TagService()
    private lateinit var linkService: LinkService

    @Before
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")

        val resourceManager = mockk<ResourceManager>()
        every { resourceManager.moveTempFiles(any(), any()) } returns true
        val workerRegistry = mockk<WorkerRegistry>()
        linkService = LinkService(tagService, resourceManager, workerRegistry)
    }

    @Test
    fun testCreateBasicLink() {
        val link = linkService.add(newLink("n1", "google.com"))
        assertThat(link.type).isEqualTo(EntryType.LINK)
        assertThat(link.title).isEqualTo("n1")
        assertThat(link.url).isEqualTo("google.com")
    }

    @Test
    fun testCreateLinkWithTags() {
        val link = linkService.add(newLink("n1", "google.com", listOf("t1", "t2")))
        assertThat(link.type).isEqualTo(EntryType.LINK)
        assertThat(link.title).isEqualTo("n1")
        assertThat(link.url).isEqualTo("google.com")
        assertThat(link.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")
    }

    @Test
    fun testNoExistingResourcesTriggersWorker() {
        val resourceManager = mockk<ResourceManager>()
        every { resourceManager.moveTempFiles(any(), "google.com") } returns false

        val workerRegistry = mockk<WorkerRegistry>()
        every { workerRegistry.acceptLinkWork(any()) } just Runs

        linkService = LinkService(tagService, resourceManager, workerRegistry)
        val link = linkService.add(newLink("n1", "google.com", listOf("t1", "t2")))

        verify(exactly = 1) { resourceManager.moveTempFiles(link.id, link.url) }
        verify(exactly = 1) { workerRegistry.acceptLinkWork(ofType(PersistLinkProcessingRequest::class)) }
    }

    @Test(expected = SQLException::class)
    fun testCreateLinkWithInvalidTag() {
        linkService.add(newLink("n1", "google.com", listOf("t1", "invalid")))
    }

    @Test
    fun testGetLinkById() {
        linkService.add(newLink("n1", "google.com", listOf("t1", "t2")))
        val link2 = linkService.add(newLink("n2", "google.com", listOf("t2")))
        val retrieved = linkService.get(link2.id)
        assertThat(retrieved?.id).isEqualTo(link2.id)
        assertThat(retrieved?.tags).isEqualTo(link2.tags)
        assertThat(retrieved?.url).isEqualTo(link2.url)
    }

    @Test
    fun testGetLinkDoesntExist() {
        assertThat(linkService.get("invalid")).isNull()
    }

    @Test
    fun testGetLinksPage() {
        linkService.add(newLink("n1", "google.com", listOf("t1", "t2")))
        linkService.add(newLink("n2", "amazon.com", listOf("t1", "t2")))
        linkService.add(newLink("n3", "netflix.com", listOf("t1", "t2")))

        var links = linkService.get(PageRequest(0, 1))
        assertThat(links).hasSize(1)
        assertThat(links).extracting("url").containsOnly("netflix.com")
        assertThat(links).extracting("title").containsOnly("n3")

        links = linkService.get(PageRequest(1, 1))
        assertThat(links).hasSize(1)
        assertThat(links).extracting("title").containsOnly("n2")

        links = linkService.get(PageRequest(0, 3))
        assertThat(links).hasSize(3)

        links = linkService.get(PageRequest(4, 3))
        assertThat(links).isEmpty()

        links = linkService.get(PageRequest(0, 10))
        assertThat(links).hasSize(3)
        assertThat(links).extracting("title").doesNotHaveDuplicates()
    }

    @Test
    fun testDeleteTags() {
        val added1 = linkService.add(newLink("n1", "google.com", listOf("t1")))
        val added2 = linkService.add(newLink("n12", "gmail.com", listOf("t1", "t2")))

        assertThat(linkService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(linkService.get(added2.id)?.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")

        tagService.deleteTag("t2")

        assertThat(linkService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(linkService.get(added2.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")

        tagService.deleteTag("t1")

        assertThat(linkService.get(added1.id)?.tags).isEmpty()
        assertThat(linkService.get(added2.id)?.tags).isEmpty()
    }

    @Test
    fun testDeleteLink() {
        assertThat(linkService.delete("invalid")).isFalse()

        val added1 = linkService.add(newLink("n1", "google.com"))
        val added2 = linkService.add(newLink("n12", "amazon.com"))

        assertThat(linkService.delete("e1")).isFalse()
        assertThat(linkService.delete(added1.id)).isTrue()

        assertThat(linkService.get(PageRequest())).hasSize(1)
        assertThat(linkService.get(added1.id)).isNull()

        assertThat(linkService.delete(added2.id)).isTrue()

        assertThat(linkService.get(PageRequest())).isEmpty()
        assertThat(linkService.get(added2.id)).isNull()
    }

    @Test
    fun testUpdateExistingLink() {
        val added1 = linkService.add(newLink("n1", "google.com"))
        assertThat(linkService.get(added1.id)?.title).isEqualTo("n1")
        assertThat(linkService.get(added1.id)?.tags).isEmpty()

        val updated = linkService.update(newLink(added1.id, "updated", "amazon.com", listOf("t1")))
        val newLink = linkService.get(updated.id)
        assertThat(newLink?.id).isEqualTo(added1.id)
        assertThat(newLink?.title).isEqualTo("updated")
        assertThat(newLink?.url).isEqualTo("amazon.com")
        assertThat(newLink?.tags).hasSize(1)

        val oldLink = linkService.get(added1.id)
        assertThat(oldLink?.id).isEqualTo(updated.id)
        assertThat(oldLink?.url).isEqualTo("amazon.com")
        assertThat(oldLink?.title).isEqualTo("updated")
        assertThat(newLink?.tags).hasSize(1)
    }

    @Test
    fun testUpdateLinkNoId() {
        val added1 = linkService.add(newLink("n1", "google.com"))
        assertThat(linkService.get(added1.id)?.title).isEqualTo("n1")

        val updated = linkService.update(newLink("updated", "amazon.com"))
        assertThat(linkService.get(updated.id)?.id).isNotEqualTo(added1.id)
        assertThat(added1.id).isNotEqualToIgnoringCase(updated.id)
        assertThat(updated.title).isEqualTo("updated")
        assertThat(updated.url).isEqualTo("amazon.com")
        assertThat(linkService.get(added1.id)?.title).isEqualTo("n1")
    }

    private fun newLink(title: String, url: String, tags: List<String> = emptyList()) = NewLink(null, title, url, tags)
    private fun newLink(id: String, title: String, url: String, tags: List<String> = emptyList()) = NewLink(id, title, url, tags)

}