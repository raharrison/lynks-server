package lynks.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import lynks.common.*
import lynks.common.exception.InvalidModelException
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.entry.EntryAuditService
import lynks.entry.FileService
import lynks.group.CollectionService
import lynks.group.GroupSetService
import lynks.group.TagService
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.util.createDummyCollection
import lynks.util.createDummyTag
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FileServiceTest : DatabaseTest() {

    private val tagService = TagService()
    private val collectionService = CollectionService()
    private val groupSetService = GroupSetService(tagService, collectionService)
    private val resourceManager = mockk<ResourceManager>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private val fileService = FileService(groupSetService, entryAuditService, resourceManager)

    @BeforeEach
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
        createDummyTag("t3", "tag3")
        createDummyCollection("c1", "col1")
        createDummyCollection("c2", "col2")
    }

    @Test
    fun testCreateBasicFile() {
        val file = fileService.add(newFile("f1", "filename"))
        assertThat(file.type).isEqualTo(EntryType.FILE)
        assertThat(file.title).isEqualTo("filename")
        assertThat(file.dateUpdated).isPositive()
        assertThat(file.dateCreated).isEqualTo(file.dateUpdated)
        verify(exactly = 0) { resourceManager.migrateGeneratedResources(file.id, any()) }
        verify { entryAuditService.acceptAuditEvent(file.id, any(), any()) }
    }

    @Test
    fun testCreateFileWithTags() {
        val file = fileService.add(newFile("f1", "filename", listOf("t1", "t2")))
        assertThat(file.type).isEqualTo(EntryType.FILE)
        assertThat(file.title).isEqualTo("filename")
        assertThat(file.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")
        assertThat(file.dateCreated).isEqualTo(file.dateUpdated)
        verify { entryAuditService.acceptAuditEvent(file.id, any(), any()) }
    }

    @Test
    fun testCreateFileWithInvalidTag() {
        assertThrows<InvalidModelException> { fileService.add(newFile("f1", "filename", listOf("t1", "invalid"))) }
    }

    @Test
    fun testCreateFileWithCollections() {
        val file = fileService.add(newFile("f1", "filename", cols = listOf("c1", "c2")))
        assertThat(file.type).isEqualTo(EntryType.FILE)
        assertThat(file.title).isEqualTo("filename")
        assertThat(file.collections).hasSize(2).extracting("id").containsExactly("c1", "c2")
        assertThat(file.dateCreated).isEqualTo(file.dateUpdated)
        verify { entryAuditService.acceptAuditEvent(file.id, any(), any()) }
    }

    @Test
    fun testCreateFileWithInvalidCollection() {
        assertThrows<InvalidModelException> {
            fileService.add(
                newFile(
                    "f1",
                    "filename",
                    cols = listOf("c1", "invalid")
                )
            )
        }
    }

    @Test
    fun testGetFileById() {
        fileService.add(newFile("f1", "filename", listOf("t1", "t2"), listOf("c1")))
        val file2 = fileService.add(newFile("f2", "filename", listOf("t2"), listOf("c2")))
        val retrieved = fileService.get(file2.id)
        assertThat(retrieved?.id).isEqualTo(file2.id)
        assertThat(retrieved?.tags).isEqualTo(file2.tags)
        assertThat(retrieved?.collections).isEqualTo(file2.collections)
        assertThat(retrieved?.title).isEqualTo(file2.title)
        assertThat(retrieved?.dateCreated).isEqualTo(file2.dateUpdated)
    }

    @Test
    fun testGetFileDoesntExist() {
        assertThat(fileService.get("invalid")).isNull()
    }

    @Test
    fun testGetFilesPage() {
        fileService.add(newFile("f1", "filename1", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        fileService.add(newFile("f2", "filename2", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        fileService.add(newFile("f3", "filename3", listOf("t1", "t2"), listOf("c1")))

        var files = fileService.get(PageRequest(1, 1))
        assertThat(files.content).hasSize(1)
        assertThat(files.page).isEqualTo(1L)
        assertThat(files.size).isEqualTo(1)
        assertThat(files.total).isEqualTo(3)
        assertThat(files.content).extracting("title").containsOnly("filename3")

        files = fileService.get(PageRequest(2, 1))
        assertThat(files.content).hasSize(1)
        assertThat(files.page).isEqualTo(2L)
        assertThat(files.size).isEqualTo(1)
        assertThat(files.total).isEqualTo(3)
        assertThat(files.content).extracting("title").containsOnly("filename2")

        files = fileService.get(PageRequest(1, 3))
        assertThat(files.content).hasSize(3)
        assertThat(files.page).isEqualTo(1L)
        assertThat(files.size).isEqualTo(3)
        assertThat(files.total).isEqualTo(3)

        files = fileService.get(PageRequest(1, 10))
        assertThat(files.content).hasSize(3)
        assertThat(files.page).isEqualTo(1L)
        assertThat(files.size).isEqualTo(10)
        assertThat(files.total).isEqualTo(3)
        assertThat(files.content).extracting("title").doesNotHaveDuplicates()
    }

    @Test
    fun testGetFilesSortOrdering() {
        fileService.add(newFile("f1", "filename1", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        fileService.add(newFile("f2", "filename2", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        fileService.add(newFile("f3", "filename3", listOf("t1", "t2"), listOf("c1")))

        val files = fileService.get(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.ASC))
        assertThat(files.content).extracting("title").containsExactly("filename1", "filename2", "filename3")

        val files2 = fileService.get(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.DESC))
        assertThat(files2.content).extracting("title").containsExactly("filename3", "filename2", "filename1")
    }

    @Test
    fun testGetFilesByGroup() {
        fileService.add(newFile("f1", "filename1", listOf("t1", "t2"), listOf("c1")))
        fileService.add(newFile("f2", "filename2", listOf("t1")))
        fileService.add(newFile("f3", "filename3", emptyList(), listOf("c2")))
        fileService.add(newFile("f4", "filename3"))

        val onlyTags = fileService.get(PageRequest(tags = listOf("t1")))
        assertThat(onlyTags.content).hasSize(2)
        assertThat(onlyTags.content).extracting("title").containsExactlyInAnyOrder("filename1", "filename2")

        val onlyTags2 = fileService.get(PageRequest(tags = listOf("t2")))
        assertThat(onlyTags2.content).hasSize(1)
        assertThat(onlyTags2.content).extracting("title").containsExactlyInAnyOrder("filename1")

        val onlyCollections = fileService.get(PageRequest(collections = listOf("c1")))
        assertThat(onlyCollections.content).hasSize(1)
        assertThat(onlyCollections.content).extracting("title").containsExactlyInAnyOrder("filename1")

        val onlyCollections2 = fileService.get(PageRequest(collections = listOf("c2")))
        assertThat(onlyCollections2.content).hasSize(1)
        assertThat(onlyCollections2.content).extracting("title").containsExactlyInAnyOrder("filename3")

        val both = fileService.get(PageRequest(tags = listOf("t1"), collections = listOf("c1")))
        assertThat(both.content).hasSize(1)
        assertThat(both.content).extracting("title").containsExactlyInAnyOrder("filename1")
    }

    @Test
    fun testDeleteTags() {
        val added1 = fileService.add(newFile("f1", "filename1", listOf("t1")))
        val added2 = fileService.add(newFile("f2", "filename2", listOf("t1", "t2")))

        assertThat(fileService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(fileService.get(added2.id)?.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")

        tagService.delete("t2")

        assertThat(fileService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(fileService.get(added2.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")

        tagService.delete("t1")

        assertThat(fileService.get(added1.id)?.tags).isEmpty()
        assertThat(fileService.get(added2.id)?.tags).isEmpty()
    }

    @Test
    fun testDeleteCollections() {
        val added1 = fileService.add(newFile("f1", "filename1", emptyList(), listOf("c1")))
        val added2 = fileService.add(newFile("f2", "filename2", emptyList(), listOf("c1", "c2")))

        assertThat(fileService.get(added1.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(fileService.get(added2.id)?.collections).hasSize(2).extracting("id").containsExactly("c1", "c2")

        collectionService.delete("c2")

        assertThat(fileService.get(added1.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(fileService.get(added2.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")

        collectionService.delete("c1")

        assertThat(fileService.get(added1.id)?.collections).isEmpty()
        assertThat(fileService.get(added2.id)?.collections).isEmpty()
    }

    @Test
    fun testDeleteFile() {
        assertThat(fileService.delete("invalid")).isFalse()

        val added1 = fileService.add(newFile("f1", "filename1"))
        val added2 = fileService.add(newFile("f2", "filename2"))

        every { resourceManager.deleteAll(any()) } returns true

        assertThat(fileService.delete("e1")).isFalse()
        assertThat(fileService.delete(added1.id)).isTrue()

        assertThat(fileService.get().content).hasSize(1)
        assertThat(fileService.get(added1.id)).isNull()

        assertThat(fileService.delete(added2.id)).isTrue()

        assertThat(fileService.get().content).isEmpty()
        assertThat(fileService.get(added2.id)).isNull()
        verify(exactly = 2) { resourceManager.deleteAll(any()) }
    }

    @Test
    fun testUpdateExistingFile() {
        val added1 = fileService.add(newFile("f1", "filename1"))
        assertThat(fileService.get(added1.id)?.tags).isEmpty()
        assertThat(fileService.get(added1.id)?.collections).isEmpty()

        val updated = fileService.update(newFile(added1.id, "new filename", listOf("t1"), listOf("c1")))
        val newFile = fileService.get(updated!!.id)
        assertThat(newFile?.id).isEqualTo(added1.id)
        assertThat(newFile?.title).isEqualTo("new filename")
        assertThat(newFile?.tags).hasSize(1)
        assertThat(newFile?.collections).hasSize(1)
        assertThat(newFile?.dateUpdated).isNotEqualTo(newFile?.dateCreated)
        verify { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }

        val oldFile = fileService.get(added1.id)
        assertThat(oldFile?.id).isEqualTo(updated.id)
        assertThat(oldFile?.title).isEqualTo("new filename")
        assertThat(oldFile?.tags).hasSize(1)
        assertThat(oldFile?.collections).hasSize(1)
    }

    @Test
    fun testUpdateFileTags() {
        val added1 = fileService.add(newFile("f1", "filename 1", listOf("t1", "t2")))
        assertThat(fileService.get(added1.id)?.title).isEqualTo("filename 1")
        assertThat(fileService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t1", "t2")

        fileService.update(newFile(added1.id, "filename 1", listOf("t2")))
        assertThat(fileService.get(added1.id)?.title).isEqualTo("filename 1")
        assertThat(fileService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t2")

        fileService.update(newFile(added1.id, "filename 1", listOf("t2", "t3")))
        assertThat(fileService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t2", "t3")
        verify(exactly = 3) { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
    }

    @Test
    fun testUpdateFileCollections() {
        val added1 = fileService.add(newFile("f1", "filename 1", emptyList(), listOf("c1", "c2")))
        assertThat(fileService.get(added1.id)?.title).isEqualTo("filename 1")
        assertThat(fileService.get(added1.id)?.collections).extracting("id").containsExactlyInAnyOrder("c1", "c2")

        fileService.update(newFile(added1.id, "filename 1", emptyList(), listOf("c2")))
        assertThat(fileService.get(added1.id)?.title).isEqualTo("filename 1")
        assertThat(fileService.get(added1.id)?.collections).extracting("id").containsExactlyInAnyOrder("c2")
        verify { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }

        fileService.update(newFile(added1.id, "filename 1", emptyList(), emptyList()))
        assertThat(fileService.get(added1.id)?.collections).extracting("id").isEmpty()
    }

    @Test
    fun testUpdateFileNoId() {
        val added1 = fileService.add(newFile("f1", "filename1"))
        assertThat(fileService.get(added1.id)?.title).isEqualTo("filename1")

        val updated = fileService.update(newFile(title = "new content"))
        assertThat(fileService.get(updated!!.id)?.id).isNotEqualTo(added1.id)
        assertThat(added1.id).isNotEqualTo(updated.id)
        assertThat(updated.dateUpdated).isEqualTo(updated.dateCreated)
        assertThat(added1.dateCreated).isNotEqualTo(updated.dateCreated)
        verify { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
    }

    @Test
    fun testUpdatePropsAttributes() {
        val added = fileService.add(newFile("f1", "filename1"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")

        fileService.update(added)

        val updated = fileService.get(added.id)
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
        val added = fileService.add(newFile("f1", "filename1"))
        val task = TaskDefinition("t1", "description", "className")
        added.props.addTask(task)

        fileService.update(added)

        val updated = fileService.get(added.id)
        assertThat(updated?.props?.getTask("t1")).isEqualTo(task)
        assertThat(updated?.props?.getAttribute("t3")).isNull()
        assertThat(updated?.dateUpdated).isEqualTo(updated?.dateCreated)
    }

    @Test
    fun testMergeProps() {
        val added = fileService.add(newFile("f1", "filename1"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")
        val task = TaskDefinition("t1", "description", "className")
        added.props.addTask(task)
        fileService.update(added)

        val updatedProps = BaseProperties()
        updatedProps.addAttribute("key2", "updated")
        updatedProps.addAttribute("key3", "attribute3")
        val updatedTask = TaskDefinition("t3", "description", "className")
        updatedProps.addTask(updatedTask)

        fileService.mergeProps(added.id, updatedProps)

        val updated = fileService.get(added.id)
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
        val added = fileService.add(newFile("f1", "filename"))
        ResourceManager().saveGeneratedResource("r1", added.id, "resource name", "jpg", ResourceType.SCREENSHOT, 11)
        val version1 = fileService.get(added.id, 1)
        assertThat(added.version).isOne()
        assertThat(added).usingRecursiveComparison().ignoringFields("props").isEqualTo(version1)
        assertThat(added.dateUpdated).isEqualTo(added.dateCreated)

        // update via new entity
        val updated = fileService.update(newFile(added.id, "filename2"))
        val version2 = fileService.get(added.id, 2)
        assertThat(updated?.version).isEqualTo(2)
        assertThat(version2).usingRecursiveComparison().ignoringFields("props").isEqualTo(updated)
        assertThat(updated?.dateUpdated).isNotEqualTo(updated?.dateCreated)

        // get original
        val first = fileService.get(added.id, 1)
        assertThat(first?.version).isOne()
        assertThat(first?.dateCreated).isEqualTo(first?.dateUpdated)

        // update directly
        fileService.update(updated!!.copy(title = "new title"), true)
        val version3 = fileService.get(added.id)
        assertThat(version3?.version).isEqualTo(3)
        assertThat(version3?.dateUpdated).isNotEqualTo(updated.dateUpdated)

        // get version before
        val stepBack = fileService.get(added.id, 2)
        assertThat(stepBack?.version).isEqualTo(2)
        assertThat(stepBack?.dateUpdated).isNotEqualTo(version3?.dateUpdated)

        // get current version
        val current = fileService.get(added.id)
        assertThat(current?.version).isEqualTo(3)
        assertThat(current?.dateUpdated).isNotEqualTo(stepBack?.dateUpdated)
        assertThat(current?.dateCreated).isNotEqualTo(version3?.dateUpdated)
    }

    @Test
    fun testGetInvalidVersion() {
        val added = fileService.add(newFile("f1", "filename"))
        assertThat(fileService.get(added.id, 0)).isNull()
        assertThat(fileService.get(added.id, 2)).isNull()
        assertThat(fileService.get(added.id, -1)).isNull()
        assertThat(fileService.get("invalid", 0)).isNull()
    }

    private fun newFile(
        title: String,
        tags: List<String> = emptyList(),
        cols: List<String> = emptyList()
    ) = NewFile(null, title, tags, cols)

    private fun newFile(
        id: String,
        title: String,
        tags: List<String> = emptyList(),
        cols: List<String> = emptyList()
    ) = NewFile(id, title, tags, cols)

}
