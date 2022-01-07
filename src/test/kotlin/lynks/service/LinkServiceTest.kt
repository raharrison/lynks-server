package lynks.service

import io.mockk.*
import lynks.common.*
import lynks.common.exception.InvalidModelException
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.group.CollectionService
import lynks.group.GroupSetService
import lynks.group.TagService
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.util.createDummyCollection
import lynks.util.createDummyTag
import lynks.worker.WorkerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LinkServiceTest : DatabaseTest() {

    private val tagService = TagService()
    private val collectionService = CollectionService()
    private val groupSetService = GroupSetService(tagService, collectionService)
    private val resourceManager = ResourceManager()
    private val workerRegistry = mockk<WorkerRegistry>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private lateinit var linkService: LinkService

    @BeforeEach
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
        createDummyTag("t3", "tag3")

        createDummyCollection("c1", "col1")
        createDummyCollection("c2", "col2")

        every { workerRegistry.acceptLinkWork(any()) } just Runs
        every { workerRegistry.acceptDiscussionWork(any()) } just Runs
        linkService = LinkService(groupSetService, entryAuditService, resourceManager, workerRegistry)
    }

    @Test
    fun testCreateBasicLink() {
        val link = linkService.add(newLink("n1", "google.com/page"))
        assertThat(link.type).isEqualTo(EntryType.LINK)
        assertThat(link.title).isEqualTo("n1")
        assertThat(link.url).isEqualTo("google.com/page")
        assertThat(link.source).isEqualTo("google.com")
        assertThat(link.dateUpdated).isPositive()
        assertThat(link.dateCreated).isEqualTo(link.dateUpdated)
        assertThat(link.thumbnailId).isNull()
        verify(exactly = 1) { workerRegistry.acceptLinkWork(any()) }
        verify(exactly = 1) { workerRegistry.acceptDiscussionWork(link.id) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
    }

    @Test
    fun testCreateLinkWithTags() {
        val link = linkService.add(newLink("n1", "google.com", listOf("t1", "t2")))
        assertThat(link.type).isEqualTo(EntryType.LINK)
        assertThat(link.title).isEqualTo("n1")
        assertThat(link.url).isEqualTo("google.com")
        assertThat(link.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")
        assertThat(link.dateCreated).isEqualTo(link.dateUpdated)
        assertThat(link.thumbnailId).isNull()
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
    }

    @Test
    fun testCreateLinkWithCollections() {
        val link = linkService.add(newLink("n1", "google.com", emptyList(), listOf("c1", "c2")))
        assertThat(link.type).isEqualTo(EntryType.LINK)
        assertThat(link.title).isEqualTo("n1")
        assertThat(link.url).isEqualTo("google.com")
        assertThat(link.collections).hasSize(2).extracting("id").containsExactly("c1", "c2")
        assertThat(link.dateCreated).isEqualTo(link.dateUpdated)
        assertThat(link.thumbnailId).isNull()
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
    }

    @Test
    fun testCreateWithNoProcessFlag() {
        val resourceManager = mockk<ResourceManager>()
        val workerRegistry = mockk<WorkerRegistry>()
        every { workerRegistry.acceptLinkWork(any()) } just Runs
        linkService = LinkService(groupSetService, entryAuditService, resourceManager, workerRegistry)
        val link = linkService.add(newLink("n1", "google.com", "url", listOf("t1", "t2"), listOf("c1"), false))

        verify(exactly = 1) { workerRegistry.acceptLinkWork(any()) }
        verify(exactly = 0) { workerRegistry.acceptDiscussionWork(link.id) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
    }

    @Test
    fun testCreateLinkWithInvalidTag() {
        assertThrows<InvalidModelException> { linkService.add(newLink("n1", "google.com", listOf("t1", "invalid"))) }
    }

    @Test
    fun testCreateLinkWithInvalidCollection() {
        assertThrows<InvalidModelException> {
            linkService.add(
                newLink(
                    "n1",
                    "google.com",
                    emptyList(),
                    listOf("c1", "invalid")
                )
            )
        }
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
        assertThat(retrieved?.dateCreated).isEqualTo(link2.dateUpdated)
        assertThat(retrieved?.thumbnailId).isNull()
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

        var links = linkService.get(PageRequest(1, 1))
        assertThat(links.content).hasSize(1)
        assertThat(links.page).isEqualTo(1L)
        assertThat(links.size).isEqualTo(1)
        assertThat(links.total).isEqualTo(3)
        assertThat(links.content).extracting("source").containsOnly("netflix.com")
        assertThat(links.content).extracting("title").containsOnly("n3")

        links = linkService.get(PageRequest(2, 1))
        assertThat(links.content).hasSize(1)
        assertThat(links.page).isEqualTo(2L)
        assertThat(links.size).isEqualTo(1)
        assertThat(links.total).isEqualTo(3)
        assertThat(links.content).extracting("title").containsOnly("n2")

        links = linkService.get(PageRequest(1, 3))
        assertThat(links.content).hasSize(3)
        assertThat(links.page).isEqualTo(1L)
        assertThat(links.size).isEqualTo(3)
        assertThat(links.total).isEqualTo(3)

        links = linkService.get(PageRequest(1, 10))
        assertThat(links.content).hasSize(3)
        assertThat(links.page).isEqualTo(1L)
        assertThat(links.size).isEqualTo(10)
        assertThat(links.total).isEqualTo(3)
        assertThat(links.content).extracting("title").doesNotHaveDuplicates()
    }

    @Test
    fun testGetLinksSortOrdering() {
        linkService.add(newLink("n1", "google.com", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        linkService.add(newLink("n2", "amazon.com", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        linkService.add(newLink("n3", "netflix.com", listOf("t1", "t2"), listOf("c1")))

        val links = linkService.get(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.ASC))
        assertThat(links.content).extracting("title").containsExactly("n1", "n2", "n3")

        val links2 = linkService.get(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.DESC))
        assertThat(links2.content).extracting("title").containsExactly("n3", "n2", "n1")
    }

    @Test
    fun testGetLinksByGroup() {
        linkService.add(newLink("l1", "google.com", listOf("t1", "t2"), listOf("c1")))
        linkService.add(newLink("l2", "amazon.com", listOf("t1")))
        linkService.add(newLink("l3", "netflix.com", emptyList(), listOf("c2")))
        linkService.add(newLink("l4", "fb.com"))

        val onlyTags = linkService.get(PageRequest(tag = "t1"))
        assertThat(onlyTags.content).hasSize(2)
        assertThat(onlyTags.content).extracting("title").containsExactlyInAnyOrder("l1", "l2")

        val onlyTags2 = linkService.get(PageRequest(tag = "t2"))
        assertThat(onlyTags2.content).hasSize(1)
        assertThat(onlyTags2.content).extracting("title").containsExactlyInAnyOrder("l1")

        val onlyCollections = linkService.get(PageRequest(collection = "c1"))
        assertThat(onlyCollections.content).hasSize(1)
        assertThat(onlyCollections.content).extracting("title").containsExactlyInAnyOrder("l1")

        val onlyCollections2 = linkService.get(PageRequest(collection = "c2"))
        assertThat(onlyCollections2.content).hasSize(1)
        assertThat(onlyCollections2.content).extracting("title").containsExactlyInAnyOrder("l3")

        val both = linkService.get(PageRequest(tag = "t1", collection = "c1"))
        assertThat(both.content).hasSize(1)
        assertThat(both.content).extracting("title").containsExactlyInAnyOrder("l1")
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

        assertThat(linkService.get().content).hasSize(1)
        assertThat(linkService.get(added1.id)).isNull()

        assertThat(linkService.delete(added2.id)).isTrue()

        assertThat(linkService.get().content).isEmpty()
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
        assertThat(newLink?.dateUpdated).isNotEqualTo(newLink?.dateCreated)
        assertThat(newLink?.thumbnailId).isEqualTo(added1.thumbnailId)

        val oldLink = linkService.get(added1.id)
        assertThat(oldLink?.id).isEqualTo(updated.id)
        assertThat(oldLink?.url).isEqualTo("amazon.com")
        assertThat(oldLink?.title).isEqualTo("updated")
        assertThat(oldLink?.tags).hasSize(1)
        assertThat(oldLink?.collections).hasSize(1)

        // for initial add and then update
        verify(exactly = 2) { workerRegistry.acceptLinkWork(any()) }
        verify(exactly = 2) { workerRegistry.acceptDiscussionWork(added1.id) }
        verify(exactly = 2) { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
    }

    @Test
    fun testUpdateLinkNoProcessFlag() {
        val added1 = linkService.add(newLink("n1", "google.com"))
        assertThat(linkService.get(added1.id)?.title).isEqualTo("n1")

        val updated = linkService.update(newLink(added1.id, "updated", "amazon.com").copy(process = false))
        val newLink = linkService.get(updated!!.id)
        assertThat(newLink?.id).isEqualTo(added1.id)
        assertThat(newLink?.title).isEqualTo("updated")
        assertThat(newLink?.url).isEqualTo("amazon.com")
        assertThat(newLink?.dateUpdated).isNotEqualTo(newLink?.dateCreated)

        // for initial add then update
        verify(exactly = 2) { workerRegistry.acceptLinkWork(any()) }
        verify(exactly = 1) { workerRegistry.acceptDiscussionWork(added1.id) }
        verify(exactly = 2) { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
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
        assertThat(updated.dateCreated).isEqualTo(updated.dateUpdated)
        val retrieved = linkService.get(added.id)
        assertThat(retrieved?.content).isEqualTo(updated.content)
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(added.id, any(), any()) }
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
        verify(exactly = 3) { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
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
        verify(exactly = 3) { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
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
        assertThat(updated.dateUpdated).isEqualTo(updated.dateCreated)
        assertThat(added1.dateCreated).isNotEqualTo(updated.dateCreated)
        assertThat(updated.thumbnailId).isEqualTo(added1.thumbnailId)
        assertThat(linkService.get(added1.id)?.title).isEqualTo("n1")
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(updated.id, any(), any()) }
    }

    @Test
    fun testUpdatePropsAttributesDoesntUpdate() {
        val added = linkService.add(newLink("n1", "google.com"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")

        linkService.update(added)

        val updated = linkService.get(added.id)
        assertThat(updated?.dateUpdated).isEqualTo(updated?.dateCreated)
        assertThat(updated?.props?.containsAttribute("key1")).isFalse()
        assertThat(updated?.props?.containsAttribute("key2")).isFalse()
        assertThat(updated?.props?.containsAttribute("key3")).isFalse()
    }

    @Test
    fun testUpdatePropsTasksDoesntUpdate() {
        val added = linkService.add(newLink("n1", "google.com"))
        val task = TaskDefinition("t1", "description", "className")
        added.props.addTask(task)

        linkService.update(added)

        val updated = linkService.get(added.id)
        assertThat(updated?.dateUpdated).isEqualTo(updated?.dateCreated)
        assertThat(updated?.props?.getTask("t1")).isNull()
        assertThat(updated?.props?.getAttribute("t3")).isNull()
    }

    @Test
    fun testMergeProps() {
        val added = linkService.add(newLink("n1", "google.com"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")
        val task = TaskDefinition("t1", "description", "className")
        added.props.addTask(task)
        linkService.mergeProps(added.id, added.props)

        val updatedProps = BaseProperties()
        updatedProps.addAttribute("key2", "updated")
        updatedProps.addAttribute("key3", "attribute3")
        val updatedTask = TaskDefinition("t3", "description", "className")
        updatedProps.addTask(updatedTask)

        linkService.mergeProps(added.id, updatedProps)

        val updated = linkService.get(added.id)
        assertThat(updated?.props?.attributes).hasSize(3)
        assertThat(updated?.props?.getAttribute("key1")).isEqualTo("attribute1")
        assertThat(updated?.props?.getAttribute("key2")).isEqualTo("updated")
        assertThat(updated?.props?.getAttribute("key3")).isEqualTo("attribute3")

        assertThat(updated?.props?.tasks).hasSize(1)
        assertThat(updated?.props?.getTask("t1")).isNull()
        assertThat(updated?.props?.getTask("t3")?.description).isEqualTo("description")
        assertThat(updated?.props?.getTask("t3")?.params).isEmpty()
    }

    @Test
    fun testVersioning() {
        val added = linkService.add(newLink("n1", "google.com"))
        resourceManager.saveGeneratedResource("r1", added.id, "resource name", "jpg", ResourceType.SCREENSHOT, 11)
        val version1 = linkService.get(added.id, 1)
        assertThat(added.version).isOne()
        assertThat(added).usingRecursiveComparison().ignoringFields("props").isEqualTo(version1)
        assertThat(added.dateUpdated).isEqualTo(added.dateCreated)

        // update via new entity
        val updated = linkService.update(newLink(added.id, "edited", "fb.com"))
        val version2 = linkService.get(added.id, 2)
        assertThat(updated?.version).isEqualTo(2)
        assertThat(version2).usingRecursiveComparison().ignoringFields("props").isEqualTo(updated)
        assertThat(version2?.title).isEqualTo("edited")
        assertThat(updated?.dateUpdated).isNotEqualTo(updated?.dateCreated)

        // get original
        val first = linkService.get(added.id, 1)
        assertThat(first?.title).isEqualTo("n1")
        assertThat(first?.version).isOne()
        assertThat(first?.dateCreated).isEqualTo(first?.dateUpdated)

        // update directly
        val updatedDirect = linkService.update(updated!!.copy(title = "new title", thumbnailId = "r1"), true)
        val version3 = linkService.get(added.id)
        assertThat(version3?.title).isEqualTo(updatedDirect?.title)
        assertThat(version3?.version).isEqualTo(3)
        assertThat(version3?.dateUpdated).isNotEqualTo(updated.dateUpdated)
        assertThat(version3?.thumbnailId).isEqualTo(updatedDirect?.thumbnailId)

        // get version before
        val stepBack = linkService.get(added.id, 2)
        assertThat(stepBack?.version).isEqualTo(2)
        assertThat(stepBack?.title).isEqualTo(version2?.title)
        assertThat(stepBack?.dateUpdated).isNotEqualTo(version3?.dateUpdated)
        assertThat(stepBack?.thumbnailId).isNotEqualTo(version3?.thumbnailId)

        // get current version
        val current = linkService.get(added.id)
        assertThat(current?.version).isEqualTo(3)
        assertThat(current?.title).isEqualTo(version3?.title)
        assertThat(current?.dateUpdated).isNotEqualTo(stepBack?.dateUpdated)
        assertThat(current?.dateCreated).isNotEqualTo(version3?.dateUpdated)
        assertThat(current?.thumbnailId).isEqualTo(version3?.thumbnailId)
    }

    @Test
    fun testGetInvalidVersion() {
        val added = linkService.add(newLink("n1", "google.com"))
        assertThat(linkService.get(added.id, 0)).isNull()
        assertThat(linkService.get(added.id, 2)).isNull()
        assertThat(linkService.get(added.id, -1)).isNull()
        assertThat(linkService.get("invalid", 0)).isNull()
    }

    @Test
    fun testSetRead() {
        val added = linkService.add(newLink("n1", "google.com"))
        assertThat(added.props.containsAttribute(READ_LINK_PROP)).isFalse()

        val read = linkService.read(added.id, true)
        assertThat(read?.props?.getAttribute(READ_LINK_PROP)).isEqualTo(true)
        // date and version is still the same
        assertThat(read?.version).isOne()
        assertThat(read?.dateUpdated).isEqualTo(added.dateUpdated)

        val unread = linkService.read(added.id, false)
        assertThat(unread?.props?.getAttribute(READ_LINK_PROP)).isEqualTo(false)
        assertThat(unread?.version).isOne()
        assertThat(unread?.dateUpdated).isEqualTo(added.dateUpdated)

        assertThat(linkService.get(added.id)?.props?.getAttribute(READ_LINK_PROP)).isEqualTo(false)
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

    @Test
    fun testGetDeadLinks() {
        val added = linkService.add(newLink("n1", "google.com"))
        assertThat(linkService.getDead()).isEmpty()

        added.props.addAttribute(DEAD_LINK_PROP, true)
        linkService.mergeProps(added.id, added.props)
        assertThat(linkService.getDead()).hasSize(1).extracting("id").containsExactly(added.id)

        added.props.addAttribute(DEAD_LINK_PROP, false)
        linkService.mergeProps(added.id, added.props)
        assertThat(linkService.getDead()).isEmpty()
    }

    private fun newLink(
        title: String,
        url: String,
        tags: List<String> = emptyList(),
        cols: List<String> = emptyList()
    ) = NewLink(null, title, url, tags, cols)

    private fun newLink(
        id: String,
        title: String,
        url: String,
        tags: List<String> = emptyList(),
        cols: List<String> = emptyList(),
        process: Boolean = true
    ) = NewLink(id, title, url, tags, cols, process)

}
