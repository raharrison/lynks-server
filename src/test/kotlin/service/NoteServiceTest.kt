package service

import common.DatabaseTest
import common.EntryType
import common.NewNote
import common.PageRequest
import entry.NoteService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import tag.TagService
import java.sql.SQLException

class NoteServiceTest : DatabaseTest() {

    private val tagService = TagService()
    private val noteService = NoteService(tagService)

    @Before
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
    }

    @Test
    fun testCreateBasicNote() {
        val note = noteService.add(newNote("n1", "content"))
        assertThat(note.type).isEqualTo(EntryType.NOTE)
        assertThat(note.title).isEqualTo("n1")
        assertThat(note.plainText).isEqualTo("content")
        assertThat(note.markdownText).isEqualTo("<p>content</p>\n")
    }

    @Test
    fun testCreateNoteWithTags() {
        val note = noteService.add(newNote("n1", "content", listOf("t1", "t2")))
        assertThat(note.type).isEqualTo(EntryType.NOTE)
        assertThat(note.title).isEqualTo("n1")
        assertThat(note.plainText).isEqualTo("content")
        assertThat(note.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")
    }

    @Test(expected = SQLException::class)
    fun testCreateNoteWithInvalidTag() {
        noteService.add(newNote("n1", "content", listOf("t1", "invalid")))
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
        noteService.add(newNote("n2", "content2", listOf("t1", "t2")))
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
    fun testUpdateNoteNoId() {
        val added1 = noteService.add(newNote("n1", "comment content 1"))
        assertThat(noteService.get(added1.id)?.title).isEqualTo("n1")

        val updated = noteService.update(newNote("updated", "new content"))
        assertThat(noteService.get(updated.id)?.id).isNotEqualTo(added1.id)
        assertThat(added1.id).isNotEqualTo(updated.id)
        assertThat(updated.title).isEqualTo("updated")
        assertThat(noteService.get(added1.id)?.title).isEqualTo("n1")
    }

    private fun newNote(title: String, content: String, tags: List<String> = emptyList()) = NewNote(null, title, content, tags)
    private fun newNote(id: String, title: String, content: String, tags: List<String> = emptyList()) = NewNote(id, title, content, tags)

}