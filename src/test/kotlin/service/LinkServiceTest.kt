package service

import common.*
import entry.LinkService
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import resource.ResourceManager
import tag.TagService
import util.createDummyTag
import worker.PersistLinkProcessingRequest
import worker.WorkerRegistry
import java.sql.SQLException

class LinkServiceTest : DatabaseTest() {

    private val tagService = TagService()
    private lateinit var linkService: LinkService

    @BeforeEach
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
        createDummyTag("t3", "tag3", "t2")

        val resourceManager = mockk<ResourceManager>()
        every { resourceManager.moveTempFiles(any(), any()) } returns true
        every { resourceManager.deleteAll(any()) } returns true
        val workerRegistry = mockk<WorkerRegistry>()
        every { workerRegistry.acceptDiscussionWork(any()) } just Runs
        linkService = LinkService(tagService, resourceManager, workerRegistry)
    }

    @Test
    fun testCreateBasicLink() {
        val link = linkService.add(newLink("n1", "google.com/page"))
        assertThat(link.type).isEqualTo(EntryType.LINK)
        assertThat(link.title).isEqualTo("n1")
        assertThat(link.url).isEqualTo("google.com/page")
        assertThat(link.source).isEqualTo("google.com")
        assertThat(link.dateUpdated).isPositive()
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
        every { workerRegistry.acceptDiscussionWork(any()) } just Runs

        linkService = LinkService(tagService, resourceManager, workerRegistry)
        val link = linkService.add(newLink("n1", "google.com", listOf("t1", "t2")))

        verify(exactly = 1) { resourceManager.moveTempFiles(link.id, link.url) }
        verify(exactly = 1) { workerRegistry.acceptLinkWork(ofType(PersistLinkProcessingRequest::class)) }
        verify(exactly = 1) { workerRegistry.acceptDiscussionWork(link) }
    }

    @Test
    fun testNoProcessFlag() {
        val resourceManager = mockk<ResourceManager>()
        every { resourceManager.moveTempFiles(any(), any()) } returns true
        val workerRegistry = mockk<WorkerRegistry>()
        linkService = LinkService(tagService, resourceManager, workerRegistry)
        val link = linkService.add(newLink("n1", "google.com", "url", listOf("t1", "t2"), false))

        verify(exactly = 1) { resourceManager.moveTempFiles(link.id, link.url) }
        verify(exactly = 0) { workerRegistry.acceptLinkWork(any()) }
        verify(exactly = 0) { workerRegistry.acceptDiscussionWork(link) }
    }

    @Test
    fun testCreateLinkWithInvalidTag() {
        assertThrows<SQLException> { linkService.add(newLink("n1", "google.com", listOf("t1", "invalid"))) }
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
        Thread.sleep(10)
        linkService.add(newLink("n2", "amazon.com", listOf("t1", "t2")))
        Thread.sleep(10)
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
    fun testGetLinksByTag() {
        linkService.add(newLink("l1", "google.com", listOf("t1", "t2")))
        linkService.add(newLink("l2", "amazon.com", listOf("t1")))
        linkService.add(newLink("l3", "netflix.com", listOf("t3")))

        val notes = linkService.get(PageRequest(tag = "t1"))
        assertThat(notes).hasSize(2)
        assertThat(notes).extracting("title").containsExactlyInAnyOrder("l1", "l2")

        val notes2 = linkService.get(PageRequest(tag = "t2"))
        assertThat(notes2).hasSize(2)
        assertThat(notes2).extracting("title").containsExactlyInAnyOrder("l1", "l3")

        val notes3 = linkService.get(PageRequest(tag = "t3"))
        assertThat(notes3).hasSize(1)
        assertThat(notes3).extracting("title").containsExactlyInAnyOrder("l3")
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
        val newLink = linkService.get(updated!!.id)
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
    fun testUpdateLinkInternal() {
        // keeping same Link instance - update content
        val added = linkService.add(newLink("n1", "google.com"))
        assertThat(added.content).isNull()
        added.content = "modified"
        val updated = linkService.update(added)
        assertThat(added.content).isEqualTo(updated!!.content)
        assertThat(updated.content).isEqualTo("modified")
        val retrieved = linkService.get(added.id)
        assertThat(retrieved?.content).isEqualTo(updated.content)
    }

    @Test
    fun testUpdateLinkTags() {
        val added1 = linkService.add(newLink("n1", "google.com", listOf("t1", "t2")))
        assertThat(linkService.get(added1.id)?.title).isEqualTo("n1")
        assertThat(linkService.get(added1.id)?.url).isEqualTo("google.com")
        assertThat(linkService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t1", "t2")

        linkService.update(newLink(added1.id, "n1", "google.com", listOf("t2")))
        assertThat(linkService.get(added1.id)?.title).isEqualTo("n1")
        assertThat(linkService.get(added1.id)?.url).isEqualTo("google.com")
        assertThat(linkService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t2")

        linkService.update(newLink(added1.id, "n1", "google.com", listOf("t2", "t3")))
        assertThat(linkService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t2", "t3")
    }

    @Test
    fun testUpdateLinkNoId() {
        val added1 = linkService.add(newLink("n1", "google.com"))
        assertThat(linkService.get(added1.id)?.title).isEqualTo("n1")

        val updated = linkService.update(newLink("updated", "amazon.com"))
        assertThat(linkService.get(updated!!.id)?.id).isNotEqualTo(added1.id)
        assertThat(added1.id).isNotEqualToIgnoringCase(updated.id)
        assertThat(updated.title).isEqualTo("updated")
        assertThat(updated.url).isEqualTo("amazon.com")
        assertThat(linkService.get(added1.id)?.title).isEqualTo("n1")
    }

    @Test
    fun testUpdatePropsAttributes() {
        val added = linkService.add(newLink("n1", "google.com"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")

        linkService.update(added)

        val updated = linkService.get(added.id)
        assertThat(updated?.props?.containsAttribute("key1")).isTrue()
        assertThat(updated?.props?.containsAttribute("key2")).isTrue()
        assertThat(updated?.props?.containsAttribute("key3")).isFalse()
        assertThat(updated?.props?.getAttribute("key1")).isEqualTo("attribute1")
        assertThat(updated?.props?.getAttribute("key2")).isEqualTo("attribute2")
        assertThat(updated?.props?.getAttribute("key3")).isNull()
    }

    @Test
    fun testUpdatePropsTasks() {
        val added = linkService.add(newLink("n1", "google.com"))
        val task = TaskDefinition("t1", "description", "className", mapOf("a1" to "v1"))
        added.props.addTask(task)

        linkService.update(added)

        val updated = linkService.get(added.id)
        assertThat(updated?.props?.getTask("t1")).isEqualTo(task)
        assertThat(updated?.props?.getAttribute("t3")).isNull()
    }

    @Test
    fun testVersioning() {
        val added = linkService.add(newLink("n1", "google.com"))
        val version1 = linkService.get(added.id, 0)
        assertThat(added.version).isZero()
        assertThat(added).isEqualToIgnoringGivenFields(version1, "props")

        val updated = linkService.update(newLink(added.id, "edited", "something"))
        val version2 = linkService.get(added.id, 1)
        assertThat(updated?.version).isOne()
        assertThat(version2).isEqualToIgnoringGivenFields(updated, "props")

        assertThat(version2?.title).isEqualTo("edited")
        val first = linkService.get(added.id, 0)
        assertThat(first?.title).isEqualTo("n1")
        assertThat(first?.version).isZero()
    }

    @Test
    fun testGetInvalidVersion() {
        val added = linkService.add(newLink("n1", "google.com"))
        assertThat(linkService.get(added.id, 1)).isNull()
        assertThat(linkService.get(added.id, -1)).isNull()
        assertThat(linkService.get("invalid", 0)).isNull()
    }

    @Test
    fun testSetRead() {
        val added = linkService.add(newLink("n1", "google.com"))
        assertThat(added.props.containsAttribute("read")).isFalse()

        val read = linkService.read(added.id, true)
        assertThat(read?.props?.getAttribute("read")).isEqualTo(true)
        // date and version is still the same
        assertThat(read?.version).isZero()
        assertThat(read?.dateUpdated).isEqualTo(added.dateUpdated)

        val unread = linkService.read(added.id, false)
        assertThat(unread?.props?.getAttribute("read")).isEqualTo(false)
        assertThat(unread?.version).isZero()
        assertThat(unread?.dateUpdated).isEqualTo(added.dateUpdated)

        assertThat(linkService.get(added.id)?.props?.getAttribute("read")).isEqualTo(false)
    }

    @Test
    fun testSetReadInvalidLink() {
        assertThat(linkService.read("invalid", true)).isNull()
        assertThat(linkService.read("invalid", false)).isNull()
    }

    private fun newLink(title: String, url: String, tags: List<String> = emptyList()) = NewLink(null, title, url, tags)
    private fun newLink(id: String, title: String, url: String, tags: List<String> = emptyList()) = NewLink(id, title, url, tags)
    private fun newLink(id: String, title: String, url: String, tags: List<String> = emptyList(), process: Boolean) = NewLink(id, title, url, tags, process)

}