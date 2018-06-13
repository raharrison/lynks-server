package service

import common.*
import entry.NoteService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import resource.ResourceManager
import tag.TagService
import java.sql.SQLException

class NoteServiceTest : DatabaseTest() {

    private val tagService = TagService()
    private val resourceManager = mockk<ResourceManager>()
    private val noteService = NoteService(tagService, resourceManager)

    @BeforeEach
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
        createDummyTag("t3", "tag3", "t2")
        every { resourceManager.deleteAll(any()) } returns true
    }

    @Test
    fun testCreateBasicNote() {
        val note = noteService.add(newNote("n1", "content"))
        assertThat(note.type).isEqualTo(EntryType.NOTE)
        assertThat(note.title).isEqualTo("n1")
        assertThat(note.plainText).isEqualTo("content")
        assertThat(note.markdownText).isEqualTo("<p>content</p>\n")
        assertThat(note.dateUpdated).isPositive()
    }

    @Test
    fun testCreateNoteWithTags() {
        val note = noteService.add(newNote("n1", "content", listOf("t1", "t2")))
        assertThat(note.type).isEqualTo(EntryType.NOTE)
        assertThat(note.title).isEqualTo("n1")
        assertThat(note.plainText).isEqualTo("content")
        assertThat(note.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")
    }

    @Test
    fun testCreateNoteWithInvalidTag() {
        assertThrows<SQLException> { noteService.add(newNote("n1", "content", listOf("t1", "invalid"))) }
    }

    @Test
    fun testGetNoteById() {
        noteService.add(newNote("n1", "content1", listOf("t1", "t2")))
        val note2 = noteService.add(newNote("n2", "content1", listOf("t2")))
        val retrieved = noteService.get(note2.id)
        assertThat(retrieved?.id).isEqualTo(note2.id)
        assertThat(retrieved?.tags).isEqualTo(note2.tags)
        assertThat(retrieved?.plainText).isEqualTo(note2.plainText)
    }

    @Test
    fun testGetNoteDoesntExist() {
        assertThat(noteService.get("invalid")).isNull()
    }

    @Test
    fun testGetNotesPage() {
        noteService.add(newNote("n1", "content1", listOf("t1", "t2")))
        Thread.sleep(10)
        noteService.add(newNote("n2", "content2", listOf("t1", "t2")))
        Thread.sleep(10)
        noteService.add(newNote("n3", "content3", listOf("t1", "t2")))

        var notes = noteService.get(PageRequest(0, 1))
        assertThat(notes).hasSize(1)
        assertThat(notes).extracting("plainText").containsOnly("content3")
        assertThat(notes).extracting("title").containsOnly("n3")

        notes = noteService.get(PageRequest(1, 1))
        assertThat(notes).hasSize(1)
        assertThat(notes).extracting("title").containsOnly("n2")

        notes = noteService.get(PageRequest(0, 3))
        assertThat(notes).hasSize(3)

        notes = noteService.get(PageRequest(4, 3))
        assertThat(notes).isEmpty()

        notes = noteService.get(PageRequest(0, 10))
        assertThat(notes).hasSize(3)
        assertThat(notes).extracting("title").doesNotHaveDuplicates()
    }

    @Test
    fun testGetNotesByTag() {
        noteService.add(newNote("n1", "content1", listOf("t1", "t2")))
        noteService.add(newNote("n2", "content2", listOf("t1")))
        noteService.add(newNote("n3", "content3", listOf("t3")))

        val notes = noteService.get(PageRequest(tag = "t1"))
        assertThat(notes).hasSize(2)
        assertThat(notes).extracting("title").containsExactlyInAnyOrder("n1", "n2")

        val notes2 = noteService.get(PageRequest(tag = "t2"))
        assertThat(notes2).hasSize(2)
        assertThat(notes2).extracting("title").containsExactlyInAnyOrder("n1", "n3")

        val notes3 = noteService.get(PageRequest(tag = "t3"))
        assertThat(notes3).hasSize(1)
        assertThat(notes3).extracting("title").containsExactlyInAnyOrder("n3")
    }

    @Test
    fun testDeleteTags() {
        val added1 = noteService.add(newNote("n1", "comment content 1", listOf("t1")))
        val added2 = noteService.add(newNote("n12", "comment content 2", listOf("t1", "t2")))

        assertThat(noteService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(noteService.get(added2.id)?.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")

        tagService.deleteTag("t2")

        assertThat(noteService.get(added1.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(noteService.get(added2.id)?.tags).hasSize(1).extracting("id").containsExactly("t1")

        tagService.deleteTag("t1")

        assertThat(noteService.get(added1.id)?.tags).isEmpty()
        assertThat(noteService.get(added2.id)?.tags).isEmpty()
    }

    @Test
    fun testDeleteNote() {
        assertThat(noteService.delete("invalid")).isFalse()

        val added1 = noteService.add(newNote("n1", "comment content 1"))
        val added2 = noteService.add(newNote("n12", "comment content 2"))

        assertThat(noteService.delete("e1")).isFalse()
        assertThat(noteService.delete(added1.id)).isTrue()

        assertThat(noteService.get(PageRequest())).hasSize(1)
        assertThat(noteService.get(added1.id)).isNull()

        assertThat(noteService.delete(added2.id)).isTrue()

        assertThat(noteService.get(PageRequest())).isEmpty()
        assertThat(noteService.get(added2.id)).isNull()
    }

    @Test
    fun testUpdateExistingNote() {
        val added1 = noteService.add(newNote("n1", "comment content 1"))
        assertThat(noteService.get(added1.id)?.title).isEqualTo("n1")
        assertThat(noteService.get(added1.id)?.tags).isEmpty()

        val updated = noteService.update(newNote(added1.id, "updated", "new content", listOf("t1")))
        val newNote = noteService.get(updated.id)
        assertThat(newNote?.id).isEqualTo(added1.id)
        assertThat(newNote?.title).isEqualTo("updated")
        assertThat(newNote?.plainText).isEqualTo("new content")
        assertThat(newNote?.tags).hasSize(1)

        val oldNote = noteService.get(added1.id)
        assertThat(oldNote?.id).isEqualTo(updated.id)
        assertThat(oldNote?.plainText).isEqualTo("new content")
        assertThat(oldNote?.title).isEqualTo("updated")
        assertThat(newNote?.tags).hasSize(1)
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
    }

    @Test
    fun testUpdateNoteNoId() {
        val added1 = noteService.add(newNote("n1", "comment content 1"))
        assertThat(noteService.get(added1.id)?.title).isEqualTo("n1")

        val updated = noteService.update(newNote("updated", "new content"))
        assertThat(noteService.get(updated.id)?.id).isNotEqualTo(added1.id)
        assertThat(added1.id).isNotEqualTo(updated.id)
        assertThat(updated.title).isEqualTo("updated")
        assertThat(noteService.get(added1.id)?.title).isEqualTo("n1")
    }

    @Test
    fun testUpdatePropsAttributes() {
        val added = noteService.add(newNote("n1", "comment content 1"))
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
    }

    @Test
    fun testUpdatePropsTasks() {
        val added = noteService.add(newNote("n1", "comment content 1"))
        val task = TaskDefinition("t1", "description", "className", mapOf("a1" to "v1"))
        added.props.addTask(task)

        noteService.update(added)

        val updated = noteService.get(added.id)
        assertThat(updated?.props?.getTask("t1")).isEqualTo(task)
        assertThat(updated?.props?.getAttribute("t3")).isNull()
    }

    private fun newNote(title: String, content: String, tags: List<String> = emptyList()) = NewNote(null, title, content, tags)
    private fun newNote(id: String, title: String, content: String, tags: List<String> = emptyList()) = NewNote(id, title, content, tags)

}