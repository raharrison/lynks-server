package service

import common.*
import common.exception.InvalidModelException
import common.page.PageRequest
import common.page.SortDirection
import entry.EntryAuditService
import entry.NoteService
import group.CollectionService
import group.GroupSetService
import group.TagService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.MapEntry.entry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import resource.Resource
import resource.ResourceManager
import resource.ResourceType
import util.createDummyCollection
import util.createDummyTag
import java.nio.file.Path

class NoteServiceTest : DatabaseTest() {

    private val tagService = TagService()
    private val collectionService = CollectionService()
    private val groupSetService = GroupSetService(tagService, collectionService)
    private val resourceManager = mockk<ResourceManager>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private val noteService = NoteService(groupSetService, entryAuditService, resourceManager)

    @BeforeEach
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
        createDummyTag("t3", "tag3")
        createDummyCollection("c1", "col1")
        createDummyCollection("c2", "col2")
    }

    @Test
    fun testCreateBasicNote() {
        val note = noteService.add(newNote("n1", "content"))
        assertThat(note.type).isEqualTo(EntryType.NOTE)
        assertThat(note.title).isEqualTo("n1")
        assertThat(note.plainText).isEqualTo("content")
        assertThat(note.markdownText).isEqualTo("<p>content</p>\n")
        assertThat(note.dateUpdated).isPositive()
        assertThat(note.dateCreated).isEqualTo(note.dateUpdated)
        assertThat(note.thumbnailId).isNull()
        verify(exactly = 0) { resourceManager.migrateGeneratedResources(note.id, any()) }
        verify { entryAuditService.acceptAuditEvent(note.id, any(), any()) }
    }

    @Test
    fun testCreateNoteWithTempImage() {
        val plain = "something ![desc](${TEMP_URL}abc/one.png)"
        val resource = Resource("rid", "eid", "one", "png", ResourceType.UPLOAD, 12, 123L, 123L)
        every { resourceManager.constructTempBasePath(IMAGE_UPLOAD_BASE) } returns Path.of("migrated/")
        every { resourceManager.migrateGeneratedResources(any(), any()) } returns listOf(resource)
        val note = noteService.add(newNote("n1", plain))
        assertThat(note.type).isEqualTo(EntryType.NOTE)
        assertThat(note.title).isEqualTo("n1")
        assertThat(note.plainText).isEqualTo("something ![desc](${Environment.server.rootPath}/entry/${note.id}/resource/${resource.id})")
        verify(exactly = 1) { resourceManager.migrateGeneratedResources(note.id, any()) }
        verify { entryAuditService.acceptAuditEvent(note.id, any(), any()) }
    }

    @Test
    fun testCreateNoteWithTags() {
        val note = noteService.add(newNote("n1", "content", listOf("t1", "t2")))
        assertThat(note.type).isEqualTo(EntryType.NOTE)
        assertThat(note.title).isEqualTo("n1")
        assertThat(note.plainText).isEqualTo("content")
        assertThat(note.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")
        assertThat(note.dateCreated).isEqualTo(note.dateUpdated)
        assertThat(note.thumbnailId).isNull()
        verify { entryAuditService.acceptAuditEvent(note.id, any(), any()) }
    }

    @Test
    fun testCreateNoteWithInvalidTag() {
        assertThrows<InvalidModelException> { noteService.add(newNote("n1", "content", listOf("t1", "invalid"))) }
    }

    @Test
    fun testCreateNoteWithCollections() {
        val note = noteService.add(newNote("n1", "content", cols = listOf("c1", "c2")))
        assertThat(note.type).isEqualTo(EntryType.NOTE)
        assertThat(note.title).isEqualTo("n1")
        assertThat(note.plainText).isEqualTo("content")
        assertThat(note.collections).hasSize(2).extracting("id").containsExactly("c1", "c2")
        assertThat(note.dateCreated).isEqualTo(note.dateUpdated)
        assertThat(note.thumbnailId).isNull()
        verify { entryAuditService.acceptAuditEvent(note.id, any(), any()) }
    }

    @Test
    fun testCreateNoteWithInvalidCollection() {
        assertThrows<InvalidModelException> {
            noteService.add(
                newNote(
                    "n1",
                    "content",
                    cols = listOf("c1", "invalid")
                )
            )
        }
    }

    @Test
    fun testGetNoteById() {
        noteService.add(newNote("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        val note2 = noteService.add(newNote("n2", "content1", listOf("t2"), listOf("c2")))
        val retrieved = noteService.get(note2.id)
        assertThat(retrieved?.id).isEqualTo(note2.id)
        assertThat(retrieved?.tags).isEqualTo(note2.tags)
        assertThat(retrieved?.collections).isEqualTo(note2.collections)
        assertThat(retrieved?.plainText).isEqualTo(note2.plainText)
        assertThat(retrieved?.dateCreated).isEqualTo(note2.dateUpdated)
        assertThat(retrieved?.thumbnailId).isNull()
    }

    @Test
    fun testGetNoteDoesntExist() {
        assertThat(noteService.get("invalid")).isNull()
    }

    @Test
    fun testGetNotesPage() {
        noteService.add(newNote("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        noteService.add(newNote("n2", "content2", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        noteService.add(newNote("n3", "content3", listOf("t1", "t2"), listOf("c1")))

        var notes = noteService.get(PageRequest(1, 1))
        assertThat(notes.content).hasSize(1)
        assertThat(notes.page).isEqualTo(1L)
        assertThat(notes.size).isEqualTo(1)
        assertThat(notes.total).isEqualTo(3)
        assertThat(notes.content).extracting("title").containsOnly("n3")

        notes = noteService.get(PageRequest(2, 1))
        assertThat(notes.content).hasSize(1)
        assertThat(notes.page).isEqualTo(2L)
        assertThat(notes.size).isEqualTo(1)
        assertThat(notes.total).isEqualTo(3)
        assertThat(notes.content).extracting("title").containsOnly("n2")

        notes = noteService.get(PageRequest(1, 3))
        assertThat(notes.content).hasSize(3)
        assertThat(notes.page).isEqualTo(1L)
        assertThat(notes.size).isEqualTo(3)
        assertThat(notes.total).isEqualTo(3)

        notes = noteService.get(PageRequest(1, 10))
        assertThat(notes.content).hasSize(3)
        assertThat(notes.page).isEqualTo(1L)
        assertThat(notes.size).isEqualTo(10)
        assertThat(notes.total).isEqualTo(3)
        assertThat(notes.content).extracting("title").doesNotHaveDuplicates()
    }

    @Test
    fun testGetNotesSortOrdering() {
        noteService.add(newNote("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        noteService.add(newNote("n2", "content2", listOf("t1", "t2"), listOf("c1")))
        Thread.sleep(10)
        noteService.add(newNote("n3", "content3", listOf("t1", "t2"), listOf("c1")))

        val notes = noteService.get(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.ASC))
        assertThat(notes.content).extracting("title").containsExactly("n1", "n2", "n3")

        val notes2 = noteService.get(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.DESC))
        assertThat(notes2.content).extracting("title").containsExactly("n3", "n2", "n1")
    }

    @Test
    fun testGetNotesByGroup() {
        noteService.add(newNote("n1", "content1", listOf("t1", "t2"), listOf("c1")))
        noteService.add(newNote("n2", "content2", listOf("t1")))
        noteService.add(newNote("n3", "content3", emptyList(), listOf("c2")))
        noteService.add(newNote("n4", "content3"))

        val onlyTags = noteService.get(PageRequest(tag = "t1"))
        assertThat(onlyTags.content).hasSize(2)
        assertThat(onlyTags.content).extracting("title").containsExactlyInAnyOrder("n1", "n2")

        val onlyTags2 = noteService.get(PageRequest(tag = "t2"))
        assertThat(onlyTags2.content).hasSize(1)
        assertThat(onlyTags2.content).extracting("title").containsExactlyInAnyOrder("n1")

        val onlyCollections = noteService.get(PageRequest(collection = "c1"))
        assertThat(onlyCollections.content).hasSize(1)
        assertThat(onlyCollections.content).extracting("title").containsExactlyInAnyOrder("n1")

        val onlyCollections2 = noteService.get(PageRequest(collection = "c2"))
        assertThat(onlyCollections2.content).hasSize(1)
        assertThat(onlyCollections2.content).extracting("title").containsExactlyInAnyOrder("n3")

        val both = noteService.get(PageRequest(tag = "t1", collection = "c1"))
        assertThat(both.content).hasSize(1)
        assertThat(both.content).extracting("title").containsExactlyInAnyOrder("n1")
    }

    @Test
    fun testDeleteTags() {
        val added1 = noteService.add(newNote("n1", "note content 1", listOf("t1")))
        val added2 = noteService.add(newNote("n12", "note content 2", listOf("t1", "t2")))

        assertThat(noteService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(noteService.get(added2.id)?.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")

        tagService.delete("t2")

        assertThat(noteService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(noteService.get(added2.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")

        tagService.delete("t1")

        assertThat(noteService.get(added1.id)?.tags).isEmpty()
        assertThat(noteService.get(added2.id)?.tags).isEmpty()
    }

    @Test
    fun testDeleteCollections() {
        val added1 = noteService.add(newNote("n1", "note content 1", emptyList(), listOf("c1")))
        val added2 = noteService.add(newNote("n12", "note content 2", emptyList(), listOf("c1", "c2")))

        assertThat(noteService.get(added1.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(noteService.get(added2.id)?.collections).hasSize(2).extracting("id").containsExactly("c1", "c2")

        collectionService.delete("c2")

        assertThat(noteService.get(added1.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(noteService.get(added2.id)?.collections).hasSize(1).extracting("id").containsExactly("c1")

        collectionService.delete("c1")

        assertThat(noteService.get(added1.id)?.collections).isEmpty()
        assertThat(noteService.get(added2.id)?.collections).isEmpty()
    }

    @Test
    fun testDeleteNote() {
        assertThat(noteService.delete("invalid")).isFalse()

        val added1 = noteService.add(newNote("n1", "note content 1"))
        val added2 = noteService.add(newNote("n12", "note content 2"))

        every { resourceManager.deleteAll(any()) } returns true

        assertThat(noteService.delete("e1")).isFalse()
        assertThat(noteService.delete(added1.id)).isTrue()

        assertThat(noteService.get().content).hasSize(1)
        assertThat(noteService.get(added1.id)).isNull()

        assertThat(noteService.delete(added2.id)).isTrue()

        assertThat(noteService.get().content).isEmpty()
        assertThat(noteService.get(added2.id)).isNull()
        verify(exactly = 2) { resourceManager.deleteAll(any()) }
    }

    @Test
    fun testUpdateExistingNote() {
        val added1 = noteService.add(newNote("n1", "note content 1"))
        assertThat(noteService.get(added1.id)?.title).isEqualTo("n1")
        assertThat(noteService.get(added1.id)?.tags).isEmpty()
        assertThat(noteService.get(added1.id)?.collections).isEmpty()

        val updated = noteService.update(newNote(added1.id, "updated", "new content", listOf("t1"), listOf("c1")))
        val newNote = noteService.get(updated!!.id)
        assertThat(newNote?.id).isEqualTo(added1.id)
        assertThat(newNote?.title).isEqualTo("updated")
        assertThat(newNote?.plainText).isEqualTo("new content")
        assertThat(newNote?.tags).hasSize(1)
        assertThat(newNote?.collections).hasSize(1)
        assertThat(newNote?.dateUpdated).isNotEqualTo(newNote?.dateCreated)
        assertThat(newNote?.thumbnailId).isEqualTo(added1.thumbnailId)
        verify { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }

        val oldNote = noteService.get(added1.id)
        assertThat(oldNote?.id).isEqualTo(updated.id)
        assertThat(oldNote?.plainText).isEqualTo("new content")
        assertThat(oldNote?.title).isEqualTo("updated")
        assertThat(oldNote?.tags).hasSize(1)
        assertThat(oldNote?.collections).hasSize(1)
    }

    @Test
    fun testUpdateExistingNoteWithTempImage() {
        val added = noteService.add(newNote("n1", "note content 1"))
        val resource = Resource("rid", added.id, "one", "png", ResourceType.UPLOAD, 12, 123L, 123L)
        every { resourceManager.constructTempBasePath(IMAGE_UPLOAD_BASE) } returns Path.of("migrated/")
        every { resourceManager.migrateGeneratedResources(added.id, any()) } returns listOf(resource)
        val updated = noteService.update(newNote(added.id, "updated", "something ![desc](${TEMP_URL}abc/one.png)"))
        assertThat(updated?.plainText).isEqualTo("something ![desc](${Environment.server.rootPath}/entry/${added.id}/resource/${resource.id})")
        verify(exactly = 1) { resourceManager.migrateGeneratedResources(added.id, any()) }
    }

    @Test
    fun testUpdateNoteTags() {
        val added1 = noteService.add(newNote("n1", "content 1", listOf("t1", "t2")))
        assertThat(noteService.get(added1.id)?.title).isEqualTo("n1")
        assertThat(noteService.get(added1.id)?.plainText).isEqualTo("content 1")
        assertThat(noteService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t1", "t2")

        noteService.update(newNote(added1.id, "n1", "content 1", listOf("t2")))
        assertThat(noteService.get(added1.id)?.title).isEqualTo("n1")
        assertThat(noteService.get(added1.id)?.plainText).isEqualTo("content 1")
        assertThat(noteService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t2")

        noteService.update(newNote(added1.id, "n1", "content 1", listOf("t2", "t3")))
        assertThat(noteService.get(added1.id)?.tags).extracting("id").containsExactlyInAnyOrder("t2", "t3")
        verify(exactly = 3) { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }
    }

    @Test
    fun testUpdateNoteCollections() {
        val added1 = noteService.add(newNote("n1", "content 1", emptyList(), listOf("c1", "c2")))
        assertThat(noteService.get(added1.id)?.title).isEqualTo("n1")
        assertThat(noteService.get(added1.id)?.plainText).isEqualTo("content 1")
        assertThat(noteService.get(added1.id)?.collections).extracting("id").containsExactlyInAnyOrder("c1", "c2")

        noteService.update(newNote(added1.id, "n1", "content 1", emptyList(), listOf("c2")))
        assertThat(noteService.get(added1.id)?.title).isEqualTo("n1")
        assertThat(noteService.get(added1.id)?.plainText).isEqualTo("content 1")
        assertThat(noteService.get(added1.id)?.collections).extracting("id").containsExactlyInAnyOrder("c2")
        verify { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }

        noteService.update(newNote(added1.id, "n1", "content 1", emptyList(), emptyList()))
        assertThat(noteService.get(added1.id)?.collections).extracting("id").isEmpty()
    }

    @Test
    fun testUpdateNoteNoId() {
        val added1 = noteService.add(newNote("n1", "note content 1"))
        assertThat(noteService.get(added1.id)?.title).isEqualTo("n1")

        val updated = noteService.update(newNote("updated", "new content"))
        assertThat(noteService.get(updated!!.id)?.id).isNotEqualTo(added1.id)
        assertThat(added1.id).isNotEqualTo(updated.id)
        assertThat(updated.title).isEqualTo("updated")
        assertThat(updated.dateUpdated).isEqualTo(updated.dateCreated)
        assertThat(added1.dateCreated).isNotEqualTo(updated.dateCreated)
        assertThat(updated.thumbnailId).isEqualTo(added1.thumbnailId)
        verify { entryAuditService.acceptAuditEvent(added1.id, any(), any()) }

        assertThat(noteService.get(added1.id)?.title).isEqualTo("n1")
    }

    @Test
    fun testUpdatePropsAttributes() {
        val added = noteService.add(newNote("n1", "note content 1"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")

        noteService.update(added)

        val updated = noteService.get(added.id)
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
        val added = noteService.add(newNote("n1", "note content 1"))
        val task = TaskDefinition("t1", "description", "className", mapOf("a1" to "v1"))
        added.props.addTask(task)

        noteService.update(added)

        val updated = noteService.get(added.id)
        assertThat(updated?.props?.getTask("t1")).isEqualTo(task)
        assertThat(updated?.props?.getAttribute("t3")).isNull()
        assertThat(updated?.dateUpdated).isEqualTo(updated?.dateCreated)
    }

    @Test
    fun testMergeProps() {
        val added = noteService.add(newNote("n1", "note content 1"))
        added.props.addAttribute("key1", "attribute1")
        added.props.addAttribute("key2", "attribute2")
        val task = TaskDefinition("t1", "description", "className", mapOf("a1" to "v1"))
        added.props.addTask(task)
        noteService.update(added)

        val updatedProps = BaseProperties()
        updatedProps.addAttribute("key2", "updated")
        updatedProps.addAttribute("key3", "attribute3")
        val updatedTask = TaskDefinition("t3", "description", "className", mapOf("b1" to "c1"))
        updatedProps.addTask(updatedTask)

        noteService.mergeProps(added.id, updatedProps)

        val updated = noteService.get(added.id)
        assertThat(updated?.props?.attributes).hasSize(3)
        assertThat(updated?.props?.getAttribute("key1")).isEqualTo("attribute1")
        assertThat(updated?.props?.getAttribute("key2")).isEqualTo("updated")
        assertThat(updated?.props?.getAttribute("key3")).isEqualTo("attribute3")

        assertThat(updated?.props?.tasks).hasSize(1)
        assertThat(updated?.props?.getTask("t1")).isNull()
        assertThat(updated?.props?.getTask("t3")?.description).isEqualTo("description")
        assertThat(updated?.props?.getTask("t3")?.input).containsOnly(entry("b1", "c1"))
    }

    @Test
    fun testVersioning() {
        val added = noteService.add(newNote("n1", "some content"))
        ResourceManager().saveGeneratedResource("r1", added.id, "resource name", "jpg", ResourceType.SCREENSHOT, 11)
        val version1 = noteService.get(added.id, 1)
        assertThat(added.version).isOne()
        assertThat(added).usingRecursiveComparison().ignoringFields("props").isEqualTo(version1)
        assertThat(added.dateUpdated).isEqualTo(added.dateCreated)

        // update via new entity
        val updated = noteService.update(newNote(added.id, "edited", "different content"))
        val version2 = noteService.get(added.id, 2)
        assertThat(updated?.version).isEqualTo(2)
        assertThat(version2).usingRecursiveComparison().ignoringFields("props").isEqualTo(updated)
        assertThat(version2?.title).isEqualTo("edited")
        assertThat(updated?.dateUpdated).isNotEqualTo(updated?.dateCreated)

        // get original
        val first = noteService.get(added.id, 1)
        assertThat(first?.title).isEqualTo("n1")
        assertThat(first?.version).isOne()
        assertThat(first?.dateCreated).isEqualTo(first?.dateUpdated)

        // update directly
        val updatedDirect = noteService.update(updated!!.copy(title = "new title", thumbnailId = "r1"), true)
        val version3 = noteService.get(added.id)
        assertThat(version3?.title).isEqualTo(updatedDirect?.title)
        assertThat(version3?.version).isEqualTo(3)
        assertThat(version3?.dateUpdated).isNotEqualTo(updated.dateUpdated)
        assertThat(version3?.thumbnailId).isEqualTo(updatedDirect?.thumbnailId)

        // get version before
        val stepBack = noteService.get(added.id, 2)
        assertThat(stepBack?.version).isEqualTo(2)
        assertThat(stepBack?.title).isEqualTo(version2?.title)
        assertThat(stepBack?.dateUpdated).isNotEqualTo(version3?.dateUpdated)
        assertThat(stepBack?.thumbnailId).isNotEqualTo(version3?.thumbnailId)

        // get current version
        val current = noteService.get(added.id)
        assertThat(current?.version).isEqualTo(3)
        assertThat(current?.title).isEqualTo(version3?.title)
        assertThat(current?.dateUpdated).isNotEqualTo(stepBack?.dateUpdated)
        assertThat(current?.dateCreated).isNotEqualTo(version3?.dateUpdated)
        assertThat(current?.thumbnailId).isEqualTo(version3?.thumbnailId)
    }

    @Test
    fun testGetInvalidVersion() {
        val added = noteService.add(newNote("n1", "some content"))
        assertThat(noteService.get(added.id, 0)).isNull()
        assertThat(noteService.get(added.id, 2)).isNull()
        assertThat(noteService.get(added.id, -1)).isNull()
        assertThat(noteService.get("invalid", 0)).isNull()
    }

    private fun newNote(
        title: String,
        content: String,
        tags: List<String> = emptyList(),
        cols: List<String> = emptyList()
    ) = NewNote(null, title, content, tags, cols)

    private fun newNote(
        id: String,
        title: String,
        content: String,
        tags: List<String> = emptyList(),
        cols: List<String> = emptyList()
    ) = NewNote(id, title, content, tags, cols)

}
