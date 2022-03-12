package lynks.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import lynks.common.*
import lynks.common.exception.InvalidModelException
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.entry.EntryAuditService
import lynks.entry.FactService
import lynks.group.CollectionService
import lynks.group.GroupSetService
import lynks.group.TagService
import lynks.resource.Resource
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.util.createDummyCollection
import lynks.util.createDummyTag
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path

class FactServiceTest : DatabaseTest() {

    private val tagService = TagService()
    private val collectionService = CollectionService()
    private val groupSetService = GroupSetService(tagService, collectionService)
    private val resourceManager = mockk<ResourceManager>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private val factService = FactService(groupSetService, entryAuditService, resourceManager)

    @BeforeEach
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
        createDummyTag("t3", "tag3")
        createDummyCollection("c1", "col1")
        createDummyCollection("c2", "col2")
    }

    @Test
    fun testCreateBasicFact() {
        val fact = factService.add(newFact("n1", "content"))
        assertThat(fact.type).isEqualTo(EntryType.FACT)
        assertThat(fact.plainText).isEqualTo("content")
        assertThat(fact.markdownText).isEqualTo("<p>content</p>\n")
        assertThat(fact.dateUpdated).isPositive()
        assertThat(fact.dateCreated).isEqualTo(fact.dateUpdated)
        verify(exactly = 0) { resourceManager.migrateGeneratedResources(fact.id, any()) }
        verify { entryAuditService.acceptAuditEvent(fact.id, any(), any()) }
    }

    @Test
    fun testCreateFactWithTempImage() {
        val plain = "something ![desc](${TEMP_URL}abc/one.png)"
        val resource = Resource("rid", "eid", "one", "png", ResourceType.UPLOAD, 12, 123L, 123L)
        every { resourceManager.constructTempBasePath(IMAGE_UPLOAD_BASE) } returns Path.of("migrated/")
        every { resourceManager.migrateGeneratedResources(any(), any()) } returns listOf(resource)
        val fact = factService.add(newFact("n1", plain))
        assertThat(fact.type).isEqualTo(EntryType.FACT)
        assertThat(fact.plainText).isEqualTo("something ![desc](${Environment.server.rootPath}/entry/${fact.id}/resource/${resource.id})")
        verify(exactly = 1) { resourceManager.migrateGeneratedResources(fact.id, any()) }
        verify { entryAuditService.acceptAuditEvent(fact.id, any(), any()) }
    }

    @Test
    fun testCreateFactWithTags() {
        val fact = factService.add(newFact("n1", "content", listOf("t1", "t2")))
        assertThat(fact.type).isEqualTo(EntryType.FACT)
        assertThat(fact.plainText).isEqualTo("content")
        assertThat(fact.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")
        assertThat(fact.dateCreated).isEqualTo(fact.dateUpdated)
        verify { entryAuditService.acceptAuditEvent(fact.id, any(), any()) }
    }

    @Test
    fun testCreateFactWithInvalidTag() {
        assertThrows<InvalidModelException> { factService.add(newFact("n1", "content", listOf("t1", "invalid"))) }
    }

    @Test
    fun testCreateFactWithCollections() {
        val fact = factService.add(newFact("n1", "content", cols = listOf("c1", "c2")))
        assertThat(fact.type).isEqualTo(EntryType.FACT)
        assertThat(fact.plainText).isEqualTo("content")
        assertThat(fact.collections).hasSize(2).extracting("id").containsExactly("c1", "c2")
        assertThat(fact.dateCreated).isEqualTo(fact.dateUpdated)
        verify { entryAuditService.acceptAuditEvent(fact.id, any(), any()) }
    }

    @Test
    fun testCreateFactWithInvalidCollection() {
        assertThrows<InvalidModelException> {
            factService.add(
                newFact(
                    "n1",
                    "content",
                    cols = listOf("c1", "invalid")
                )
            )
        }
    }

    @Test
    fun testGetFactById() {
        factService.add(newFact("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        val fact2 = factService.add(newFact("n2", "content1", listOf("t2"), listOf("c2")))
        val retrieved = factService.get(fact2.id)
        assertThat(retrieved?.id).isEqualTo(fact2.id)
        assertThat(retrieved?.tags).isEqualTo(fact2.tags)
        assertThat(retrieved?.collections).isEqualTo(fact2.collections)
        assertThat(retrieved?.plainText).isEqualTo(fact2.plainText)
        assertThat(retrieved?.dateCreated).isEqualTo(fact2.dateUpdated)
    }

    @Test
    fun testGetFactDoesntExist() {
        assertThat(factService.get("invalid")).isNull()
    }

    @Test
    fun testGetFactsPage() {
        factService.add(newFact("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        factService.add(newFact("n2", "content2", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        factService.add(newFact("n3", "content3", listOf("t1", "t2"), listOf("c1")))

        var facts = factService.get(PageRequest(1, 1))
        assertThat(facts.content).hasSize(1)
        assertThat(facts.page).isEqualTo(1L)
        assertThat(facts.size).isEqualTo(1)
        assertThat(facts.total).isEqualTo(3)
        assertThat(facts.content).extracting("markdownText").containsOnly("<p>content3</p>\n")

        facts = factService.get(PageRequest(2, 1))
        assertThat(facts.content).hasSize(1)
        assertThat(facts.page).isEqualTo(2L)
        assertThat(facts.size).isEqualTo(1)
        assertThat(facts.total).isEqualTo(3)
        assertThat(facts.content).extracting("markdownText").containsOnly("<p>content2</p>\n")

        facts = factService.get(PageRequest(1, 3))
        assertThat(facts.content).hasSize(3)
        assertThat(facts.page).isEqualTo(1L)
        assertThat(facts.size).isEqualTo(3)
        assertThat(facts.total).isEqualTo(3)

        facts = factService.get(PageRequest(1, 10))
        assertThat(facts.content).hasSize(3)
        assertThat(facts.page).isEqualTo(1L)
        assertThat(facts.size).isEqualTo(10)
        assertThat(facts.total).isEqualTo(3)
        assertThat(facts.content).extracting("markdownText").doesNotHaveDuplicates()
    }

    @Test
    fun testGetFactsSortOrdering() {
        factService.add(newFact("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        factService.add(newFact("n2", "content2", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        factService.add(newFact("n3", "content3", listOf("t1", "t2"), listOf("c1")))

        val facts = factService.get(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.ASC))
        assertThat(facts.content).extracting("markdownText").containsExactly("<p>content1</p>\n", "<p>content2</p>\n", "<p>content3</p>\n")

        val facts2 = factService.get(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.DESC))
        assertThat(facts2.content).extracting("markdownText").containsExactly("<p>content3</p>\n", "<p>content2</p>\n", "<p>content1</p>\n")
    }

    @Test
    fun testGetFactsByGroup() {
        factService.add(newFact("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        factService.add(newFact("n2", "content2", listOf("t1")))
        factService.add(newFact("n3", "content3", emptyList(), listOf("c2")))
        factService.add(newFact("n4", "content3"))

        val onlyTags = factService.get(PageRequest(tags = listOf("t1")))
        assertThat(onlyTags.content).hasSize(2)
        assertThat(onlyTags.content).extracting("markdownText").containsExactlyInAnyOrder("<p>content1</p>\n", "<p>content2</p>\n")

        val onlyTags2 = factService.get(PageRequest(tags = listOf("t2")))
        assertThat(onlyTags2.content).hasSize(1)
        assertThat(onlyTags2.content).extracting("markdownText").containsExactlyInAnyOrder("<p>content1</p>\n")

        val onlyCollections = factService.get(PageRequest(collections = listOf("c1")))
        assertThat(onlyCollections.content).hasSize(1)
        assertThat(onlyCollections.content).extracting("markdownText").containsExactlyInAnyOrder("<p>content1</p>\n")

        val onlyCollections2 = factService.get(PageRequest(collections = listOf("c2")))
        assertThat(onlyCollections2.content).hasSize(1)
        assertThat(onlyCollections2.content).extracting("markdownText").containsExactlyInAnyOrder("<p>content3</p>\n")

        val both = factService.get(PageRequest(tags = listOf("t1"), collections = listOf("c1")))
        assertThat(both.content).hasSize(1)
        assertThat(both.content).extracting("markdownText").containsExactlyInAnyOrder("<p>content1</p>\n")
    }

    @Test
    fun testDeleteTags() {
        val added1 = factService.add(newFact("n1", "fact content 1", listOf("t1")))
        val added2 = factService.add(newFact("n12", "fact content 2", listOf("t1", "t2")))

        assertThat(factService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(factService.get(added2.id)?.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")

        tagService.delete("t2")

        assertThat(factService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(factService.get(added2.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")

        tagService.delete("t1")

        assertThat(factService.get(added1.id)?.tags).isEmpty()
        assertThat(factService.get(added2.id)?.tags).isEmpty()
    }

    @Test
    fun testDeleteCollections() {
        val added1 = factService.add(newFact("n1", "fact content 1", emptyList(), listOf("c1")))
        val added2 = factService.add(newFact("n12", "fact content 2", emptyList(), listOf("c1", "c2")))

        assertThat(factService.get(added1.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(factService.get(added2.id)?.collections).hasSize(2).extracting("id").containsExactly("c1", "c2")

        collectionService.delete("c2")

        assertThat(factService.get(added1.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(factService.get(added2.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")

        collectionService.delete("c1")

        assertThat(factService.get(added1.id)?.collections).isEmpty()
        assertThat(factService.get(added2.id)?.collections).isEmpty()
    }

    @Test
    fun testDeleteFact() {
        assertThat(factService.delete("invalid")).isFalse()

        val added1 = factService.add(newFact("n1", "fact content 1"))
        val added2 = factService.add(newFact("n12", "fact content 2"))

        every { resourceManager.deleteAll(any()) } returns true

        assertThat(factService.delete("e1")).isFalse()
        assertThat(factService.delete(added1.id)).isTrue()

        assertThat(factService.get().content).hasSize(1)
        assertThat(factService.get(added1.id)).isNull()

        assertThat(factService.delete(added2.id)).isTrue()

        assertThat(factService.get().content).isEmpty()
        assertThat(factService.get(added2.id)).isNull()
        verify(exactly = 2) { resourceManager.deleteAll(any()) }
    }

    @Test
    fun testUpdateExistingFact() {
        val added1 = factService.add(newFact("n1", "fact content 1"))
        assertThat(factService.get(added1.id)?.tags).isEmpty()
        assertThat(factService.get(added1.id)?.collections).isEmpty()

        val updated = factService.update(newFact(added1.id, "new content", listOf("t1"), listOf("c1")))
        val newFact = factService.get(updated!!.id)
        assertThat(newFact?.id).isEqualTo(added1.id)
        assertThat(newFact?.plainText).isEqualTo("new content")
        assertThat(newFact?.tags).hasSize(1)
        assertThat(newFact?.collections).hasSize(1)
        assertThat(newFact?.dateUpdated).isNotEqualTo(newFact?.dateCreated)
        verify { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }

        val oldFact = factService.get(added1.id)
        assertThat(oldFact?.id).isEqualTo(updated.id)
        assertThat(oldFact?.plainText).isEqualTo("new content")
        assertThat(oldFact?.tags).hasSize(1)
        assertThat(oldFact?.collections).hasSize(1)
    }

    @Test
    fun testUpdateExistingFactWithTempImage() {
        val added = factService.add(newFact("n1", "fact content 1"))
        val resource = Resource("rid", added.id, "one", "png", ResourceType.UPLOAD, 12, 123L, 123L)
        every { resourceManager.constructTempBasePath(IMAGE_UPLOAD_BASE) } returns Path.of("migrated/")
        every { resourceManager.migrateGeneratedResources(added.id, any()) } returns listOf(resource)
        val updated = factService.update(newFact(added.id, "something ![desc](${TEMP_URL}abc/one.png)"))
        assertThat(updated?.plainText).isEqualTo("something ![desc](${Environment.server.rootPath}/entry/${added.id}/resource/${resource.id})")
        verify(exactly = 1) { resourceManager.migrateGeneratedResources(added.id, any()) }
    }

    @Test
    fun testUpdateFactTags() {
        val added1 = factService.add(newFact("n1", "content 1", listOf("t1", "t2")))
        assertThat(factService.get(added1.id)?.plainText).isEqualTo("content 1")
        assertThat(factService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t1", "t2")

        factService.update(newFact(added1.id, "content 1", listOf("t2")))
        assertThat(factService.get(added1.id)?.plainText).isEqualTo("content 1")
        assertThat(factService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t2")

        factService.update(newFact(added1.id, "content 1", listOf("t2", "t3")))
        assertThat(factService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t2", "t3")
        verify(exactly = 3) { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
    }

    @Test
    fun testUpdateFactCollections() {
        val added1 = factService.add(newFact("n1", "content 1", emptyList(), listOf("c1", "c2")))
        assertThat(factService.get(added1.id)?.plainText).isEqualTo("content 1")
        assertThat(factService.get(added1.id)?.collections).extracting("id").containsExactlyInAnyOrder("c1", "c2")

        factService.update(newFact(added1.id, "content 1", emptyList(), listOf("c2")))
        assertThat(factService.get(added1.id)?.plainText).isEqualTo("content 1")
        assertThat(factService.get(added1.id)?.collections).extracting("id").containsExactlyInAnyOrder("c2")
        verify { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }

        factService.update(newFact(added1.id, "content 1", emptyList(), emptyList()))
        assertThat(factService.get(added1.id)?.collections).extracting("id").isEmpty()
    }

    @Test
    fun testUpdateFactNoId() {
        val added1 = factService.add(newFact("n1", "fact content 1"))
        assertThat(factService.get(added1.id)?.plainText).isEqualTo("fact content 1")

        val updated = factService.update(newFact(content = "new content"))
        assertThat(factService.get(updated!!.id)?.id).isNotEqualTo(added1.id)
        assertThat(added1.id).isNotEqualTo(updated.id)
        assertThat(updated.dateUpdated).isEqualTo(updated.dateCreated)
        assertThat(added1.dateCreated).isNotEqualTo(updated.dateCreated)
        verify { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
    }

    @Test
    fun testUpdatePropsAttributes() {
        val added = factService.add(newFact("n1", "fact content 1"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")

        factService.update(added)

        val updated = factService.get(added.id)
        assertThat(updated?.props?.containsAttribute("key1")).isTrue()
        assertThat(updated?.props?.containsAttribute("key2")).isTrue()
        assertThat(updated?.props?.containsAttribute("key3")).isFalse()
        assertThat(updated?.props?.getAttribute("key1")).isEqualTo("attribute1")
        assertThat(updated?.props?.getAttribute("key2")).isEqualTo("attribute2")
        assertThat(updated?.props?.getAttribute("key3")).isNull()
        assertThat(updated?.dateUpdated).isEqualTo(updated?.dateCreated)
        verify { entryAuditService.acceptAuditEvent(added.id, any(), any()) }
    }

    @Test
    fun testUpdatePropsTasks() {
        val added = factService.add(newFact("n1", "fact content 1"))
        val task = TaskDefinition("t1", "description", "className")
        added.props.addTask(task)

        factService.update(added)

        val updated = factService.get(added.id)
        assertThat(updated?.props?.getTask("t1")).isEqualTo(task)
        assertThat(updated?.props?.getAttribute("t3")).isNull()
        assertThat(updated?.dateUpdated).isEqualTo(updated?.dateCreated)
    }

    @Test
    fun testMergeProps() {
        val added = factService.add(newFact("n1", "fact content 1"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")
        val task = TaskDefinition("t1", "description", "className")
        added.props.addTask(task)
        factService.update(added)

        val updatedProps = BaseProperties()
        updatedProps.addAttribute("key2", "updated")
        updatedProps.addAttribute("key3", "attribute3")
        val updatedTask = TaskDefinition("t3", "description", "className")
        updatedProps.addTask(updatedTask)

        factService.mergeProps(added.id, updatedProps)

        val updated = factService.get(added.id)
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
        val added = factService.add(newFact("n1", "some content"))
        ResourceManager().saveGeneratedResource("r1", added.id, "resource name", "jpg", ResourceType.SCREENSHOT, 11)
        val version1 = factService.get(added.id, 1)
        assertThat(added.version).isOne()
        assertThat(added).usingRecursiveComparison().ignoringFields("props").isEqualTo(version1)
        assertThat(added.dateUpdated).isEqualTo(added.dateCreated)

        // update via new entity
        val updated = factService.update(newFact(added.id, "different content"))
        val version2 = factService.get(added.id, 2)
        assertThat(updated?.version).isEqualTo(2)
        assertThat(version2).usingRecursiveComparison().ignoringFields("props").isEqualTo(updated)
        assertThat(updated?.dateUpdated).isNotEqualTo(updated?.dateCreated)

        // get original
        val first = factService.get(added.id, 1)
        assertThat(first?.version).isOne()
        assertThat(first?.dateCreated).isEqualTo(first?.dateUpdated)

        // update directly
        factService.update(updated!!.copy(plainText = "new title"), true)
        val version3 = factService.get(added.id)
        assertThat(version3?.version).isEqualTo(3)
        assertThat(version3?.dateUpdated).isNotEqualTo(updated.dateUpdated)

        // get version before
        val stepBack = factService.get(added.id, 2)
        assertThat(stepBack?.version).isEqualTo(2)
        assertThat(stepBack?.dateUpdated).isNotEqualTo(version3?.dateUpdated)

        // get current version
        val current = factService.get(added.id)
        assertThat(current?.version).isEqualTo(3)
        assertThat(current?.dateUpdated).isNotEqualTo(stepBack?.dateUpdated)
        assertThat(current?.dateCreated).isNotEqualTo(version3?.dateUpdated)
    }

    @Test
    fun testGetInvalidVersion() {
        val added = factService.add(newFact("n1", "some content"))
        assertThat(factService.get(added.id, 0)).isNull()
        assertThat(factService.get(added.id, 2)).isNull()
        assertThat(factService.get(added.id, -1)).isNull()
        assertThat(factService.get("invalid", 0)).isNull()
    }

    private fun newFact(
        content: String,
        tags: List<String> = emptyList(),
        cols: List<String> = emptyList()
    ) = NewFact(null, content, tags, cols)

    private fun newFact(
        id: String,
        content: String,
        tags: List<String> = emptyList(),
        cols: List<String> = emptyList()
    ) = NewFact(id, content, tags, cols)

}
