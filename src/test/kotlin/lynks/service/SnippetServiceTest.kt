package lynks.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import lynks.common.*
import lynks.common.exception.InvalidModelException
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.entry.EntryAuditService
import lynks.entry.SnippetService
import lynks.group.CollectionService
import lynks.group.GroupSetService
import lynks.group.TagService
import lynks.resource.Resource
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.util.createDummyCollection
import lynks.util.createDummyTag
import lynks.util.markdown.MarkdownProcessor
import lynks.worker.WorkerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path

class SnippetServiceTest : DatabaseTest() {

    private val tagService = TagService()
    private val collectionService = CollectionService()
    private val groupSetService = GroupSetService(tagService, collectionService)
    private val resourceManager = mockk<ResourceManager>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private val workerRegistry = mockk<WorkerRegistry>(relaxUnitFun = true)
    private val markdownProcessor = MarkdownProcessor(resourceManager)
    private val snippetService = SnippetService(groupSetService, entryAuditService, resourceManager, workerRegistry, markdownProcessor)

    @BeforeEach
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
        createDummyTag("t3", "tag3")
        createDummyCollection("c1", "col1")
        createDummyCollection("c2", "col2")
    }

    @Test
    fun testCreateBasicSnippet() {
        val snippet = snippetService.add(newSnippet("n1", "content"))
        assertThat(snippet.type).isEqualTo(EntryType.SNIPPET)
        assertThat(snippet.plainText).isEqualTo("content")
        assertThat(snippet.markdownText).isEqualTo("<p>content</p>\n")
        assertThat(snippet.dateUpdated).isPositive()
        assertThat(snippet.dateCreated).isEqualTo(snippet.dateUpdated)
        verify(exactly = 0) { resourceManager.migrateGeneratedResources(snippet.id, any()) }
        verify { entryAuditService.acceptAuditEvent(snippet.id, any(), any()) }
        verify { workerRegistry.acceptEntryRefWork(snippet.id) }
    }

    @Test
    fun testCreateSnippetWithTempImage() {
        val plain = "something ![desc](${TEMP_URL}abc/one.png)"
        val resource = Resource("rid", "eid", "one", "png", ResourceType.UPLOAD, 12, 123L, 123L)
        every { resourceManager.constructTempBasePath(IMAGE_UPLOAD_BASE) } returns Path.of("migrated/")
        every { resourceManager.migrateGeneratedResources(any(), any()) } returns listOf(resource)
        val snippet = snippetService.add(newSnippet("n1", plain))
        assertThat(snippet.type).isEqualTo(EntryType.SNIPPET)
        assertThat(snippet.plainText.trim()).isEqualTo("something ![desc](${Environment.server.rootPath}/entry/${snippet.id}/resource/${resource.id})")
        verify(exactly = 1) { resourceManager.migrateGeneratedResources(snippet.id, any()) }
        verify { entryAuditService.acceptAuditEvent(snippet.id, any(), any()) }
        verify { workerRegistry.acceptEntryRefWork(snippet.id) }
    }

    @Test
    fun testCreateSnippetWithTags() {
        val snippet = snippetService.add(newSnippet("n1", "content", listOf("t1", "t2")))
        assertThat(snippet.type).isEqualTo(EntryType.SNIPPET)
        assertThat(snippet.plainText).isEqualTo("content")
        assertThat(snippet.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")
        assertThat(snippet.dateCreated).isEqualTo(snippet.dateUpdated)
        verify { entryAuditService.acceptAuditEvent(snippet.id, any(), any()) }
        verify { workerRegistry.acceptEntryRefWork(snippet.id) }
    }

    @Test
    fun testCreateSnippetWithInvalidTag() {
        assertThrows<InvalidModelException> { snippetService.add(newSnippet("n1", "content", listOf("t1", "invalid"))) }
    }

    @Test
    fun testCreateSnippetWithCollections() {
        val snippet = snippetService.add(newSnippet("n1", "content", cols = listOf("c1", "c2")))
        assertThat(snippet.type).isEqualTo(EntryType.SNIPPET)
        assertThat(snippet.plainText).isEqualTo("content")
        assertThat(snippet.collections).hasSize(2).extracting("id").containsExactly("c1", "c2")
        assertThat(snippet.dateCreated).isEqualTo(snippet.dateUpdated)
        verify { entryAuditService.acceptAuditEvent(snippet.id, any(), any()) }
        verify { workerRegistry.acceptEntryRefWork(snippet.id) }
    }

    @Test
    fun testCreateSnippetWithInvalidCollection() {
        assertThrows<InvalidModelException> {
            snippetService.add(
                newSnippet(
                    "n1",
                    "content",
                    cols = listOf("c1", "invalid")
                )
            )
        }
    }

    @Test
    fun testGetSnippetById() {
        snippetService.add(newSnippet("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        val snippet2 = snippetService.add(newSnippet("n2", "content1", listOf("t2"), listOf("c2")))
        val retrieved = snippetService.get(snippet2.id)
        assertThat(retrieved?.id).isEqualTo(snippet2.id)
        assertThat(retrieved?.tags).isEqualTo(snippet2.tags)
        assertThat(retrieved?.collections).isEqualTo(snippet2.collections)
        assertThat(retrieved?.plainText).isEqualTo(snippet2.plainText)
        assertThat(retrieved?.dateCreated).isEqualTo(snippet2.dateUpdated)
    }

    @Test
    fun testGetSnippetDoesntExist() {
        assertThat(snippetService.get("invalid")).isNull()
    }

    @Test
    fun testGetSnippetsPage() {
        snippetService.add(newSnippet("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        snippetService.add(newSnippet("n2", "content2", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        snippetService.add(newSnippet("n3", "content3", listOf("t1", "t2"), listOf("c1")))

        var snippets = snippetService.get(PageRequest(1, 1))
        assertThat(snippets.content).hasSize(1)
        assertThat(snippets.page).isEqualTo(1L)
        assertThat(snippets.size).isEqualTo(1)
        assertThat(snippets.total).isEqualTo(3)
        assertThat(snippets.content).extracting("markdownText").containsOnly("<p>content3</p>\n")

        snippets = snippetService.get(PageRequest(2, 1))
        assertThat(snippets.content).hasSize(1)
        assertThat(snippets.page).isEqualTo(2L)
        assertThat(snippets.size).isEqualTo(1)
        assertThat(snippets.total).isEqualTo(3)
        assertThat(snippets.content).extracting("markdownText").containsOnly("<p>content2</p>\n")

        snippets = snippetService.get(PageRequest(1, 3))
        assertThat(snippets.content).hasSize(3)
        assertThat(snippets.page).isEqualTo(1L)
        assertThat(snippets.size).isEqualTo(3)
        assertThat(snippets.total).isEqualTo(3)

        snippets = snippetService.get(PageRequest(1, 10))
        assertThat(snippets.content).hasSize(3)
        assertThat(snippets.page).isEqualTo(1L)
        assertThat(snippets.size).isEqualTo(10)
        assertThat(snippets.total).isEqualTo(3)
        assertThat(snippets.content).extracting("markdownText").doesNotHaveDuplicates()
    }

    @Test
    fun testGetSnippetsSortOrdering() {
        snippetService.add(newSnippet("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        snippetService.add(newSnippet("n2", "content2", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        snippetService.add(newSnippet("n3", "content3", listOf("t1", "t2"), listOf("c1")))

        val snippets = snippetService.get(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.ASC))
        assertThat(snippets.content).extracting("markdownText").containsExactly("<p>content1</p>\n", "<p>content2</p>\n", "<p>content3</p>\n")

        val snippets2 = snippetService.get(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.DESC))
        assertThat(snippets2.content).extracting("markdownText").containsExactly("<p>content3</p>\n", "<p>content2</p>\n", "<p>content1</p>\n")
    }

    @Test
    fun testGetSnippetsByGroup() {
        snippetService.add(newSnippet("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        snippetService.add(newSnippet("n2", "content2", listOf("t1")))
        snippetService.add(newSnippet("n3", "content3", emptyList(), listOf("c2")))
        snippetService.add(newSnippet("n4", "content3"))

        val onlyTags = snippetService.get(PageRequest(tags = listOf("t1")))
        assertThat(onlyTags.content).hasSize(2)
        assertThat(onlyTags.content).extracting("markdownText").containsExactlyInAnyOrder("<p>content1</p>\n", "<p>content2</p>\n")

        val onlyTags2 = snippetService.get(PageRequest(tags = listOf("t2")))
        assertThat(onlyTags2.content).hasSize(1)
        assertThat(onlyTags2.content).extracting("markdownText").containsExactlyInAnyOrder("<p>content1</p>\n")

        val onlyCollections = snippetService.get(PageRequest(collections = listOf("c1")))
        assertThat(onlyCollections.content).hasSize(1)
        assertThat(onlyCollections.content).extracting("markdownText").containsExactlyInAnyOrder("<p>content1</p>\n")

        val onlyCollections2 = snippetService.get(PageRequest(collections = listOf("c2")))
        assertThat(onlyCollections2.content).hasSize(1)
        assertThat(onlyCollections2.content).extracting("markdownText").containsExactlyInAnyOrder("<p>content3</p>\n")

        val both = snippetService.get(PageRequest(tags = listOf("t1"), collections = listOf("c1")))
        assertThat(both.content).hasSize(1)
        assertThat(both.content).extracting("markdownText").containsExactlyInAnyOrder("<p>content1</p>\n")
    }

    @Test
    fun testGetSnippetsBySource() {
        snippetService.add(newSnippet("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        snippetService.add(newSnippet("n2", "content2", listOf("t1", "t2"), listOf("c1")))
        val snippetsFromSource = snippetService.get(PageRequest(source = "me"))
        assertThat(snippetsFromSource.total).isEqualTo(2)
        assertThat(snippetsFromSource.content).hasSize(2)
        val snippetsFromMissingSource = snippetService.get(PageRequest(source = "invalid"))
        assertThat(snippetsFromMissingSource.total).isZero()
        assertThat(snippetsFromMissingSource.content).isEmpty()
    }

    @Test
    fun testDeleteTags() {
        val added1 = snippetService.add(newSnippet("n1", "snippet content 1", listOf("t1")))
        val added2 = snippetService.add(newSnippet("n12", "snippet content 2", listOf("t1", "t2")))

        assertThat(snippetService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(snippetService.get(added2.id)?.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")

        tagService.delete("t2")

        assertThat(snippetService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(snippetService.get(added2.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")

        tagService.delete("t1")

        assertThat(snippetService.get(added1.id)?.tags).isEmpty()
        assertThat(snippetService.get(added2.id)?.tags).isEmpty()
    }

    @Test
    fun testDeleteCollections() {
        val added1 = snippetService.add(newSnippet("n1", "snippet content 1", emptyList(), listOf("c1")))
        val added2 = snippetService.add(newSnippet("n12", "snippet content 2", emptyList(), listOf("c1", "c2")))

        assertThat(snippetService.get(added1.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(snippetService.get(added2.id)?.collections).hasSize(2).extracting("id").containsExactly("c1", "c2")

        collectionService.delete("c2")

        assertThat(snippetService.get(added1.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(snippetService.get(added2.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")

        collectionService.delete("c1")

        assertThat(snippetService.get(added1.id)?.collections).isEmpty()
        assertThat(snippetService.get(added2.id)?.collections).isEmpty()
    }

    @Test
    fun testDeleteSnippet() {
        assertThat(snippetService.delete("invalid")).isFalse()

        val added1 = snippetService.add(newSnippet("n1", "snippet content 1"))
        val added2 = snippetService.add(newSnippet("n12", "snippet content 2"))

        every { resourceManager.deleteAll(any()) } returns true

        assertThat(snippetService.delete("e1")).isFalse()
        assertThat(snippetService.delete(added1.id)).isTrue()

        assertThat(snippetService.get().content).hasSize(1)
        assertThat(snippetService.get(added1.id)).isNull()

        assertThat(snippetService.delete(added2.id)).isTrue()

        assertThat(snippetService.get().content).isEmpty()
        assertThat(snippetService.get(added2.id)).isNull()
        verify(exactly = 2) { resourceManager.deleteAll(any()) }
    }

    @Test
    fun testUpdateExistingSnippet() {
        val added1 = snippetService.add(newSnippet("n1", "snippet content 1"))
        assertThat(snippetService.get(added1.id)?.tags).isEmpty()
        assertThat(snippetService.get(added1.id)?.collections).isEmpty()

        val updated = snippetService.update(newSnippet(added1.id, "new content", listOf("t1"), listOf("c1")))
        val newSnippet = snippetService.get(updated!!.id)
        assertThat(newSnippet?.id).isEqualTo(added1.id)
        assertThat(newSnippet?.plainText).isEqualTo("new content")
        assertThat(newSnippet?.tags).hasSize(1)
        assertThat(newSnippet?.collections).hasSize(1)
        assertThat(newSnippet?.dateUpdated).isNotEqualTo(newSnippet?.dateCreated)
        verify { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
        verify { workerRegistry.acceptEntryRefWork(added1.id) }

        val oldSnippet = snippetService.get(added1.id)
        assertThat(oldSnippet?.id).isEqualTo(updated.id)
        assertThat(oldSnippet?.plainText).isEqualTo("new content")
        assertThat(oldSnippet?.tags).hasSize(1)
        assertThat(oldSnippet?.collections).hasSize(1)
    }

    @Test
    fun testUpdateExistingSnippetWithTempImage() {
        val added = snippetService.add(newSnippet("n1", "snippet content 1"))
        val resource = Resource("rid", added.id, "one", "png", ResourceType.UPLOAD, 12, 123L, 123L)
        every { resourceManager.constructTempBasePath(IMAGE_UPLOAD_BASE) } returns Path.of("migrated/")
        every { resourceManager.migrateGeneratedResources(added.id, any()) } returns listOf(resource)
        val updated = snippetService.update(newSnippet(added.id, "something ![desc](${TEMP_URL}abc/one.png)"))
        assertThat(updated?.plainText?.trim()).isEqualTo("something ![desc](${Environment.server.rootPath}/entry/${added.id}/resource/${resource.id})")
        verify(exactly = 1) { resourceManager.migrateGeneratedResources(added.id, any()) }
        verify { workerRegistry.acceptEntryRefWork(added.id) }
    }

    @Test
    fun testUpdateSnippetTags() {
        val added1 = snippetService.add(newSnippet("n1", "content 1", listOf("t1", "t2")))
        assertThat(snippetService.get(added1.id)?.plainText).isEqualTo("content 1")
        assertThat(snippetService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t1", "t2")

        snippetService.update(newSnippet(added1.id, "content 1", listOf("t2")))
        assertThat(snippetService.get(added1.id)?.plainText).isEqualTo("content 1")
        assertThat(snippetService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t2")

        snippetService.update(newSnippet(added1.id, "content 1", listOf("t2", "t3")))
        assertThat(snippetService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t2", "t3")
        verify(exactly = 3) { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
        verify { workerRegistry.acceptEntryRefWork(added1.id) }
    }

    @Test
    fun testUpdateSnippetCollections() {
        val added1 = snippetService.add(newSnippet("n1", "content 1", emptyList(), listOf("c1", "c2")))
        assertThat(snippetService.get(added1.id)?.plainText).isEqualTo("content 1")
        assertThat(snippetService.get(added1.id)?.collections).extracting("id").containsExactlyInAnyOrder("c1", "c2")

        snippetService.update(newSnippet(added1.id, "content 1", emptyList(), listOf("c2")))
        assertThat(snippetService.get(added1.id)?.plainText).isEqualTo("content 1")
        assertThat(snippetService.get(added1.id)?.collections).extracting("id").containsExactlyInAnyOrder("c2")
        verify { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
        verify { workerRegistry.acceptEntryRefWork(added1.id) }

        snippetService.update(newSnippet(added1.id, "content 1", emptyList(), emptyList()))
        assertThat(snippetService.get(added1.id)?.collections).extracting("id").isEmpty()
    }

    @Test
    fun testUpdateSnippetNoId() {
        val added1 = snippetService.add(newSnippet("n1", "snippet content 1"))
        assertThat(snippetService.get(added1.id)?.plainText).isEqualTo("snippet content 1")

        val updated = snippetService.update(newSnippet(content = "new content"))
        assertThat(snippetService.get(updated!!.id)?.id).isNotEqualTo(added1.id)
        assertThat(added1.id).isNotEqualTo(updated.id)
        assertThat(updated.dateUpdated).isEqualTo(updated.dateCreated)
        assertThat(added1.dateCreated).isNotEqualTo(updated.dateCreated)
        verify { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
        verify { workerRegistry.acceptEntryRefWork(added1.id) }
    }

    @Test
    fun testUpdatePropsAttributes() {
        val added = snippetService.add(newSnippet("n1", "snippet content 1"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")

        snippetService.update(added)

        val updated = snippetService.get(added.id)
        assertThat(updated?.props?.containsAttribute("key1")).isTrue()
        assertThat(updated?.props?.containsAttribute("key2")).isTrue()
        assertThat(updated?.props?.containsAttribute("key3")).isFalse()
        assertThat(updated?.props?.getAttribute("key1")).isEqualTo("attribute1")
        assertThat(updated?.props?.getAttribute("key2")).isEqualTo("attribute2")
        assertThat(updated?.props?.getAttribute("key3")).isNull()
        assertThat(updated?.dateUpdated).isEqualTo(updated?.dateCreated)
        verify { entryAuditService.acceptAuditEvent(added.id, any(), any()) }
        verify { workerRegistry.acceptEntryRefWork(added.id) }
    }

    @Test
    fun testUpdatePropsTasks() {
        val added = snippetService.add(newSnippet("n1", "snippet content 1"))
        val task = TaskDefinition("t1", "description", "className")
        added.props.addTask(task)

        snippetService.update(added)

        val updated = snippetService.get(added.id)
        assertThat(updated?.props?.getTask("t1")).isEqualTo(task)
        assertThat(updated?.props?.getAttribute("t3")).isNull()
        assertThat(updated?.dateUpdated).isEqualTo(updated?.dateCreated)
    }

    @Test
    fun testMergeProps() {
        val added = snippetService.add(newSnippet("n1", "snippet content 1"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")
        val task = TaskDefinition("t1", "description", "className")
        added.props.addTask(task)
        snippetService.update(added)

        val updatedProps = BaseProperties()
        updatedProps.addAttribute("key2", "updated")
        updatedProps.addAttribute("key3", "attribute3")
        val updatedTask = TaskDefinition("t3", "description", "className")
        updatedProps.addTask(updatedTask)

        snippetService.mergeProps(added.id, updatedProps)

        val updated = snippetService.get(added.id)
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
        val added = snippetService.add(newSnippet("n1", "some content"))
        ResourceManager().saveGeneratedResource("r1", added.id, "resource name", "jpg", ResourceType.SCREENSHOT, 11)
        val version1 = snippetService.get(added.id, 1)
        assertThat(added.version).isOne()
        assertThat(added).usingRecursiveComparison().ignoringFields("props").isEqualTo(version1)
        assertThat(added.dateUpdated).isEqualTo(added.dateCreated)

        // update via new entity
        val updated = snippetService.update(newSnippet(added.id, "different content"))
        val version2 = snippetService.get(added.id, 2)
        assertThat(updated?.version).isEqualTo(2)
        assertThat(version2).usingRecursiveComparison().ignoringFields("props").isEqualTo(updated)
        assertThat(updated?.dateUpdated).isNotEqualTo(updated?.dateCreated)

        // get original
        val first = snippetService.get(added.id, 1)
        assertThat(first?.version).isOne()
        assertThat(first?.dateCreated).isEqualTo(first?.dateUpdated)

        // update directly
        snippetService.update(updated!!.copy(plainText = "new title"), true)
        val version3 = snippetService.get(added.id)
        assertThat(version3?.version).isEqualTo(3)
        assertThat(version3?.dateUpdated).isNotEqualTo(updated.dateUpdated)

        // get version before
        val stepBack = snippetService.get(added.id, 2)
        assertThat(stepBack?.version).isEqualTo(2)
        assertThat(stepBack?.dateUpdated).isNotEqualTo(version3?.dateUpdated)

        // get current version
        val current = snippetService.get(added.id)
        assertThat(current?.version).isEqualTo(3)
        assertThat(current?.dateUpdated).isNotEqualTo(stepBack?.dateUpdated)
        assertThat(current?.dateCreated).isNotEqualTo(version3?.dateUpdated)
    }

    @Test
    fun testGetInvalidVersion() {
        val added = snippetService.add(newSnippet("n1", "some content"))
        assertThat(snippetService.get(added.id, 0)).isNull()
        assertThat(snippetService.get(added.id, 2)).isNull()
        assertThat(snippetService.get(added.id, -1)).isNull()
        assertThat(snippetService.get("invalid", 0)).isNull()
    }

    private fun newSnippet(
        content: String,
        tags: List<String> = emptyList(),
        cols: List<String> = emptyList()
    ) = NewSnippet(null, content, tags, cols)

    private fun newSnippet(
        id: String,
        content: String,
        tags: List<String> = emptyList(),
        cols: List<String> = emptyList()
    ) = NewSnippet(id, content, tags, cols)

}
