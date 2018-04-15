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

    private val noteService = NoteService(TagService())

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

    private fun newNote(title: String, content: String, tags: List<String> = emptyList()) = NewNote(null, title, content, tags)

}