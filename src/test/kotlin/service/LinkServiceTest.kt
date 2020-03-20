package service

import common.*
import common.exception.InvalidModelException
import entry.LinkService
import group.CollectionService
import group.TagService
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.MapEntry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import resource.ResourceManager
import util.createDummyCollection
import util.createDummyTag
import worker.WorkerRegistry

class LinkServiceTest : DatabaseTest() {

    private val tagService = TagService()
    private val collectionService = CollectionService()
    private lateinit var linkService: LinkService

    @BeforeEach
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
        createDummyTag("t3", "tag3")

        createDummyCollection("c1", "col1")
        createDummyCollection("c2", "col2")

        val resourceManager = mockk<ResourceManager>()
        every { resourceManager.deleteAll(any()) } returns true
        val workerRegistry = mockk<WorkerRegistry>()
        every { workerRegistry.acceptLinkWork(any()) } just Runs
        every { workerRegistry.acceptDiscussionWork(any()) } just Runs
        linkService = LinkService(tagService, collectionService, resourceManager, workerRegistry)
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
    fun testCreateLinkWithCollections() {
        val link = linkService.add(newLink("n1", "google.com", emptyList(), listOf("c1", "c2")))
        assertThat(link.type).isEqualTo(EntryType.LINK)
        assertThat(link.title).isEqualTo("n1")
        assertThat(link.url).isEqualTo("google.com")
        assertThat(link.collections).hasSize(2).extracting("id").containsExactly("c1", "c2")
    }

    @Test
    fun testNoProcessFlag() {
        val resourceManager = mockk<ResourceManager>()
        val workerRegistry = mockk<WorkerRegistry>()
        every { workerRegistry.acceptLinkWork(any()) } just Runs
        linkService = LinkService(tagService, collectionService, resourceManager, workerRegistry)
        val link = linkService.add(newLink("n1", "google.com", "url", listOf("t1", "t2"), listOf("c1"), false))

        verify(exactly = 1) { workerRegistry.acceptLinkWork(any()) }
        verify(exactly = 0) { workerRegistry.acceptDiscussionWork(link.id) }
    }

    @Test
    fun testCreateLinkWithInvalidTag() {
        assertThrows<InvalidModelException> { linkService.add(newLink("n1", "google.com", listOf("t1", "invalid"))) }
    }

    @Test
    fun testCreateLinkWithInvalidCollection() {
        assertThrows<InvalidModelException> { linkService.add(newLink("n1", "google.com", emptyList(), listOf("c1", "invalid"))) }
    }

    @Test
    fun testGetLinkById() {
        linkService.add(newLink("n1", "google.com", listOf("t1", "t2"), listOf("c1")))
        val link2 = linkService.add(newLink("n2", "google.com", listOf("t2"), listOf("c2")))
        val retrieved = linkService.get(link2.id)
        assertThat(retrieved?.id).isEqualTo(link2.id)
        assertThat(retrieved?.tags).isEqualTo(link2.tags)
        assertThat(retrieved?.collections).isEqualTo(link2.collections)
        assertThat(retrieved?.url).isEqualTo(link2.url)
    }

    @Test
    fun testGetLinkDoesntExist() {
        assertThat(linkService.get("invalid")).isNull()
    }

    @Test
    fun testGetLinksPage() {
        linkService.add(newLink("n1", "google.com", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        linkService.add(newLink("n2", "amazon.com", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        linkService.add(newLink("n3", "netflix.com", listOf("t1", "t2"), listOf("c1")))

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
    fun testGetLinksByGroup() {
        linkService.add(newLink("l1", "google.com", listOf("t1", "t2"), listOf("c1")))
        linkService.add(newLink("l2", "amazon.com", listOf("t1")))
        linkService.add(newLink("l3", "netflix.com", emptyList(), listOf("c2")))
        linkService.add(newLink("l4", "fb.com"))

        val onlyTags = linkService.get(PageRequest(tag = "t1"))
        assertThat(onlyTags).hasSize(2)
        assertThat(onlyTags).extracting("title").containsExactlyInAnyOrder("l1", "l2")

        val onlyTags2 = linkService.get(PageRequest(tag = "t2"))
        assertThat(onlyTags2).hasSize(1)
        assertThat(onlyTags2).extracting("title").containsExactlyInAnyOrder("l1")

        val onlyCollections = linkService.get(PageRequest(collection = "c1"))
        assertThat(onlyCollections).hasSize(1)
        assertThat(onlyCollections).extracting("title").containsExactlyInAnyOrder("l1")

        val onlyCollections2 = linkService.get(PageRequest(collection = "c2"))
        assertThat(onlyCollections2).hasSize(1)
        assertThat(onlyCollections2).extracting("title").containsExactlyInAnyOrder("l3")

        val both = linkService.get(PageRequest(tag = "t1", collection = "c1"))
        assertThat(both).hasSize(1)
        assertThat(both).extracting("title").containsExactlyInAnyOrder("l1")
    }

    @Test
    fun testDeleteTags() {
        val added1 = linkService.add(newLink("n1", "google.com", listOf("t1")))
        val added2 = linkService.add(newLink("n12", "gmail.com", listOf("t1", "t2")))

        assertThat(linkService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(linkService.get(added2.id)?.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")

        tagService.delete("t2")

        assertThat(linkService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(linkService.get(added2.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")

        tagService.delete("t1")

        assertThat(linkService.get(added1.id)?.tags).isEmpty()
        assertThat(linkService.get(added2.id)?.tags).isEmpty()
    }

    @Test
    fun testDeleteCollections() {
        val added1 = linkService.add(newLink("n1", "google.com", emptyList(), listOf("c1")))
        val added2 = linkService.add(newLink("n12", "gmail.com", emptyList(), listOf("c1", "c2")))

        assertThat(linkService.get(added1.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(linkService.get(added2.id)?.collections).hasSize(2).extracting("id").containsExactly("c1", "c2")

        collectionService.delete("c2")

        assertThat(linkService.get(added1.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(linkService.get(added2.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")

        collectionService.delete("c1")

        assertThat(linkService.get(added1.id)?.collections).isEmpty()
        assertThat(linkService.get(added2.id)?.collections).isEmpty()
    }

    @Test
    fun testDeleteLink() {
        assertThat(linkService.delete("invalid")).isFalse()

        val added1 = linkService.add(newLink("n1", "google.com"))
        val added2 = linkService.add(newLink("n12", "amazon.com"))

        assertThat(linkService.delete("e1")).isFalse()
        assertThat(linkService.delete(added1.id)).isTrue()

        assertThat(linkService.get()).hasSize(1)
        assertThat(linkService.get(added1.id)).isNull()

        assertThat(linkService.delete(added2.id)).isTrue()

        assertThat(linkService.get()).isEmpty()
        assertThat(linkService.get(added2.id)).isNull()
    }

    @Test
    fun testUpdateExistingLink() {
        val added1 = linkService.add(newLink("n1", "google.com"))
        assertThat(linkService.get(added1.id)?.title).isEqualTo("n1")
        assertThat(linkService.get(added1.id)?.tags).isEmpty()
        assertThat(linkService.get(added1.id)?.collections).isEmpty()

        val updated = linkService.update(newLink(added1.id, "updated", "amazon.com", listOf("t1"), listOf("c2")))
        val newLink = linkService.get(updated!!.id)
        assertThat(newLink?.id).isEqualTo(added1.id)
        assertThat(newLink?.title).isEqualTo("updated")
        assertThat(newLink?.url).isEqualTo("amazon.com")
        assertThat(newLink?.tags).hasSize(1)
        assertThat(newLink?.collections).hasSize(1)

        val oldLink = linkService.get(added1.id)
        assertThat(oldLink?.id).isEqualTo(updated.id)
        assertThat(oldLink?.url).isEqualTo("amazon.com")
        assertThat(oldLink?.title).isEqualTo("updated")
        assertThat(oldLink?.tags).hasSize(1)
        assertThat(oldLink?.collections).hasSize(1)
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
    fun testUpdateLinkCollections() {
        val added1 = linkService.add(newLink("n1", "google.com", emptyList(), listOf("c1", "c2")))
        assertThat(linkService.get(added1.id)?.title).isEqualTo("n1")
        assertThat(linkService.get(added1.id)?.url).isEqualTo("google.com")
        assertThat(linkService.get(added1.id)?.collections).extracting("id").containsExactlyInAnyOrder("c1", "c2")

        linkService.update(newLink(added1.id, "n1", "google.com", emptyList(), listOf("c2")))
        assertThat(linkService.get(added1.id)?.title).isEqualTo("n1")
        assertThat(linkService.get(added1.id)?.url).isEqualTo("google.com")
        assertThat(linkService.get(added1.id)?.collections).extracting("id").containsExactlyInAnyOrder("c2")

        linkService.update(newLink(added1.id, "n1", "google.com"))
        assertThat(linkService.get(added1.id)?.collections).extracting("id").isEmpty()
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
    fun testUpdatePropsAttributesDoesntUpdate() {
        val added = linkService.add(newLink("n1", "google.com"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")

        linkService.update(added)

        val updated = linkService.get(added.id)
        assertThat(updated?.props?.containsAttribute("key1")).isFalse()
        assertThat(updated?.props?.containsAttribute("key2")).isFalse()
        assertThat(updated?.props?.containsAttribute("key3")).isFalse()
    }

    @Test
    fun testUpdatePropsTasksDoesntUpdate() {
        val added = linkService.add(newLink("n1", "google.com"))
        val task = TaskDefinition("t1", "description", "className", mapOf("a1" to "v1"))
        added.props.addTask(task)

        linkService.update(added)

        val updated = linkService.get(added.id)
        assertThat(updated?.props?.getTask("t1")).isNull()
        assertThat(updated?.props?.getAttribute("t3")).isNull()
    }
    @Test
    fun testMergeProps() {
        val added = linkService.add(newLink("n1", "google.com"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")
        val task = TaskDefinition("t1", "description", "className", mapOf("a1" to "v1"))
        added.props.addTask(task)
        linkService.mergeProps(added.id, added.props)

        val updatedProps = BaseProperties()
        updatedProps.addAttribute("key2", "updated")
        updatedProps.addAttribute("key3", "attribute3")
        val updatedTask = TaskDefinition("t1", "updatedDesc", "className", mapOf("b1" to "c1"))
        updatedProps.addTask(updatedTask)

        linkService.mergeProps(added.id, updatedProps)

        val updated = linkService.get(added.id)
        assertThat(updated?.props?.attributes).hasSize(3)
        assertThat(updated?.props?.getAttribute("key1")).isEqualTo("attribute1")
        assertThat(updated?.props?.getAttribute("key2")).isEqualTo("updated")
        assertThat(updated?.props?.getAttribute("key3")).isEqualTo("attribute3")

        assertThat(updated?.props?.tasks).hasSize(1)
        assertThat(updated?.props?.getTask("t1")?.description).isEqualTo("updatedDesc")
        assertThat(updated?.props?.getTask("t1")?.input).containsOnly(MapEntry.entry("b1", "c1"))
    }

    @Test
    fun testVersioning() {
        val added = linkService.add(newLink("n1", "google.com"))
        val version1 = linkService.get(added.id, 0)
        assertThat(added.version).isZero()
        assertThat(added).isEqualToIgnoringGivenFields(version1, "props")

        // update via new entity
        val updated = linkService.update(newLink(added.id, "edited", "fb.com"))
        val version2 = linkService.get(added.id, 1)
        assertThat(updated?.version).isOne()
        assertThat(version2).isEqualToIgnoringGivenFields(updated, "props")
        assertThat(version2?.title).isEqualTo("edited")

        // get original
        val first = linkService.get(added.id, 0)
        assertThat(first?.title).isEqualTo("n1")
        assertThat(first?.version).isZero()

        // update directly
        val updatedDirect = linkService.update(updated!!.copy(title = "new title"), true)
        val version3 = linkService.get(added.id)
        assertThat(version3?.title).isEqualTo(updatedDirect?.title)
        assertThat(version3?.version).isEqualTo(2)

        // get version before
        val stepBack = linkService.get(added.id, 1)
        assertThat(stepBack?.version).isEqualTo(1)
        assertThat(stepBack?.title).isEqualTo(version2?.title)

        // get current version
        val current = linkService.get(added.id)
        assertThat(current?.version).isEqualTo(2)
        assertThat(current?.title).isEqualTo(version3?.title)
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

    @Test
    fun testGetUnreadLinks() {
        val added = linkService.add(newLink("n1", "google.com"))

        assertThat(linkService.getUnread()).hasSize(1).extracting("id").containsOnly(added.id)

        linkService.read(added.id, false)
        assertThat(linkService.getUnread()).hasSize(1).extracting("id").containsOnly(added.id)

        linkService.read(added.id, true)
        assertThat(linkService.getUnread()).isEmpty()
    }

    private fun newLink(title: String, url: String, tags: List<String> = emptyList(), cols: List<String> = emptyList()) = NewLink(null, title, url, tags, cols)
    private fun newLink(id: String, title: String, url: String, tags: List<String> = emptyList(), cols: List<String> = emptyList()) = NewLink(id, title, url, tags, cols)
    private fun newLink(id: String, title: String, url: String, tags: List<String> = emptyList(), cols: List<String> = emptyList(), process: Boolean) = NewLink(id, title, url, tags, cols, process)

}