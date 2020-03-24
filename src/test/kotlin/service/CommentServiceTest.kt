package service

import comment.CommentService
import comment.NewComment
import common.DatabaseTest
import common.EntryType
import common.PageRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import util.createDummyEntry
import java.sql.SQLException

class CommentServiceTest : DatabaseTest() {

    private val commentService = CommentService()

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        Thread.sleep(10)// prevent having same creation timestamp
        createDummyEntry("e2", "title2", "content2", EntryType.NOTE)
    }

    @Test
    fun testCreateBasicComment() {
        val added = commentService.addComment("e1", newComment(content = "comment content"))
        assertThat(added.entryId).isEqualTo("e1")
        assertThat(added.plainText).isEqualTo("comment content")
        assertThat(added.markdownText).isEqualTo("<p>comment content</p>\n")
        assertThat(added.dateCreated).isNotZero()
        assertThat(added.dateUpdated).isNotZero()
    }

    @Test
    fun testCreateMarkdownComment() {
        val plain = "# header\n\na paragraph"
        val markdown = "<h1>header</h1>\n<p>a paragraph</p>\n"
        val added = commentService.addComment("e1", newComment(content = plain))
        assertThat(added.entryId).isEqualTo("e1")
        assertThat(added.plainText).isEqualTo(plain)
        assertThat(added.markdownText).isEqualTo(markdown)
    }

    @Test
    fun testEntryDoesntExist() {
        assertThrows<SQLException> { commentService.addComment("invalid", newComment(content = "comment content")) }
    }

    @Test
    fun testGetCommentById() {
        val added = commentService.addComment("e1", newComment(content = "comment content 1"))
        val retrieved = commentService.getComment(added.entryId, added.id)
        assertThat(retrieved).isNotNull
        assertThat(retrieved?.entryId).isEqualTo("e1")
        assertThat(retrieved?.plainText).isEqualTo(added.plainText)
        assertThat(retrieved?.dateCreated).isEqualTo(added.dateCreated)
        assertThat(retrieved?.dateUpdated).isEqualTo(added.dateUpdated)
        assertThat(retrieved?.id).isEqualTo(added.id)
        assertThat(retrieved?.markdownText).isEqualTo(added.markdownText)
    }

    @Test
    fun testGetCommentByIdDoesntExist() {
        assertThat(commentService.getComment("invalid", "invalid")).isNull()
    }

    @Test
    fun testGetCommentsForEntry() {
        assertThat(commentService.getCommentsFor("invalid")).isEmpty()

        commentService.addComment("e1", newComment(content = "comment content 1"))
        commentService.addComment("e1", newComment(content = "comment content 2"))

        val comments = commentService.getCommentsFor("e1")
        assertThat(comments).hasSize(2)
        assertThat(comments).extracting("id").doesNotHaveDuplicates()
        assertThat(comments).extracting("entryId").containsOnly("e1")
        assertThat(comments).extracting("plainText").contains("comment content 1")

        assertThat(commentService.getCommentsFor("e2")).isEmpty()
    }

    @Test
    fun testGetCommentsPage() {
        commentService.addComment("e1", newComment(content = "comment content 1"))
        Thread.sleep(10)
        commentService.addComment("e1", newComment(content = "comment content 2"))
        Thread.sleep(10)
        commentService.addComment("e1", newComment(content = "comment content 3"))

        var comments = commentService.getCommentsFor("e1", PageRequest(0, 1))
        assertThat(comments).hasSize(1)
        assertThat(comments).extracting("plainText").containsOnly("comment content 1")

        comments = commentService.getCommentsFor("e1", PageRequest(1, 1))
        assertThat(comments).hasSize(1)
        assertThat(comments).extracting("plainText").containsOnly("comment content 2")

        comments = commentService.getCommentsFor("e1", PageRequest(0, 3))
        assertThat(comments).hasSize(3)

        comments = commentService.getCommentsFor("e1", PageRequest(4, 3))
        assertThat(comments).isEmpty()

        comments = commentService.getCommentsFor("e1", PageRequest(0, 10))
        assertThat(comments).hasSize(3)
        assertThat(comments).extracting("id").doesNotHaveDuplicates()
    }

    @Test
    fun testDeleteComments() {
        assertThat(commentService.deleteComment("invalid", "invalid")).isFalse()

        val added1 = commentService.addComment("e1", newComment(content = "comment content 1"))
        val added2 = commentService.addComment("e1", newComment(content = "comment content 2"))

        assertThat(commentService.deleteComment("e1", "e1")).isFalse()
        assertThat(commentService.deleteComment(added1.entryId, added1.id)).isTrue()

        assertThat(commentService.getCommentsFor("e1")).hasSize(1)
        assertThat(commentService.getComment(added1.entryId, added1.id)).isNull()

        assertThat(commentService.deleteComment(added2.entryId, added2.id)).isTrue()

        assertThat(commentService.getCommentsFor("e1")).isEmpty()
        assertThat(commentService.getComment(added2.entryId, added2.id)).isNull()
    }

    @Test
    fun testUpdateExistingComment() {
        val added1 = commentService.addComment("e1", newComment(content = "comment content 1"))
        assertThat(commentService.getComment(added1.entryId, added1.id)?.entryId).isEqualTo("e1")
        assertThat(added1.dateCreated).isEqualTo(added1.dateUpdated)

        val updated = commentService.updateComment("e1", newComment(added1.id, "changed"))
        val newComm = commentService.getComment(updated!!.entryId, updated.id)
        assertThat(updated).isEqualTo(newComm)
        assertThat(newComm?.entryId).isEqualTo("e1")
        assertThat(newComm?.plainText).isEqualTo("changed")
        assertThat(newComm?.dateCreated).isEqualTo(added1.dateCreated)
        assertThat(newComm?.dateUpdated).isNotEqualTo(added1.dateCreated)

        val oldComm = commentService.getComment(added1.entryId, added1.id)
        assertThat(oldComm?.entryId).isEqualTo("e1")
        assertThat(oldComm?.plainText).isEqualTo("changed")
        assertThat(oldComm?.dateUpdated).isNotEqualTo(oldComm?.dateCreated)
    }

    @Test
    fun testUpdateCommentNoId() {
        val added1 = commentService.addComment("e1", newComment(content = "comment content 1"))
        assertThat(commentService.getComment(added1.entryId, added1.id)?.entryId).isEqualTo("e1")

        val updated = commentService.updateComment("e1", newComment(content = "new comment"))
        assertThat(commentService.getComment(updated!!.entryId, updated.id)?.entryId).isEqualTo("e1")
        assertThat(added1.id).isNotEqualTo(updated.id)
        assertThat(updated.dateCreated).isEqualTo(updated.dateUpdated)

        val comments = commentService.getCommentsFor("e1")
        assertThat(comments).hasSize(2)
        assertThat(comments).extracting("id").containsOnly(added1.id, updated.id)
    }

    private fun newComment(id: String? = null, content: String) = NewComment(id, content)

}