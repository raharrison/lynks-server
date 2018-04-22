package service

import comment.CommentService
import comment.NewComment
import common.DatabaseTest
import common.EntryType
import common.PageRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.sql.SQLException

class CommentServiceTest : DatabaseTest() {

    private val commentService = CommentService()

    @Before
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        createDummyEntry("e2", "title2", "content2", EntryType.NOTE)
    }

    @Test
    fun testCreateBasicComment() {
        val added = commentService.addComment("e1", newComment(content = "comment content"))
        assertThat(added.entryId).isEqualTo("e1")
        assertThat(added.plainText).isEqualTo("comment content")
        assertThat(added.markdownText).isEqualTo("<p>comment content</p>\n")
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

    @Test(expected = SQLException::class)
    fun testEntryDoesntExist() {
        commentService.addComment("invalid", newComment(content = "comment content"))
    }

    @Test
    fun testGetCommentById() {
        val added = commentService.addComment("e1", newComment(content = "comment content 1"))
        val retrieved = commentService.getComment(added.id)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.entryId).isEqualTo("e1")
        assertThat(retrieved?.plainText).isEqualTo(added.plainText)
        assertThat(retrieved?.dateCreated).isEqualTo(added.dateCreated)
        assertThat(retrieved?.id).isEqualTo(added.id)
        assertThat(retrieved?.markdownText).isEqualTo(added.markdownText)
    }

    @Test
    fun testGetCommentByIdDoesntExist() {
        assertThat(commentService.getComment("invalid")).isNull()
    }

    @Test
    fun testGetCommentsForEntry() {
        assertThat(commentService.getCommentsFor("invalid", PageRequest())).isEmpty()

        commentService.addComment("e1", newComment(content = "comment content 1"))
        commentService.addComment("e1", newComment(content = "comment content 2"))

        val comments = commentService.getCommentsFor("e1", PageRequest())
        assertThat(comments).hasSize(2)
        assertThat(comments).extracting("id").doesNotHaveDuplicates()
        assertThat(comments).extracting("entryId").containsOnly("e1")
        assertThat(comments).extracting("plainText").contains("comment content 1")

        assertThat(commentService.getCommentsFor("e2", PageRequest())).isEmpty()
    }

    @Test
    fun testGetCommentsPage() {
        commentService.addComment("e1", newComment(content = "comment content 1"))
        commentService.addComment("e1", newComment(content = "comment content 2"))
        commentService.addComment("e1", newComment(content = "comment content 3"))

        var comments = commentService.getCommentsFor("e1", PageRequest(0, 1))
        assertThat(comments).hasSize(1)
        assertThat(comments).extracting("plainText").containsOnly("comment content 3")

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
        assertThat(commentService.deleteComment("invalid")).isFalse()

        val added1 = commentService.addComment("e1", newComment(content = "comment content 1"))
        val added2 = commentService.addComment("e1", newComment(content = "comment content 2"))

        assertThat(commentService.deleteComment("e1")).isFalse()
        assertThat(commentService.deleteComment(added1.id)).isTrue()

        assertThat(commentService.getCommentsFor("e1", PageRequest())).hasSize(1)
        assertThat(commentService.getComment(added1.id)).isNull()

        assertThat(commentService.deleteComment(added2.id)).isTrue()

        assertThat(commentService.getCommentsFor("e1", PageRequest())).isEmpty()
        assertThat(commentService.getComment(added2.id)).isNull()
    }

    @Test
    fun testUpdateExistingComment() {
        val added1 = commentService.addComment("e1", newComment(content = "comment content 1"))
        assertThat(commentService.getComment(added1.id)?.entryId).isEqualTo("e1")

        val updated = commentService.updateComment("e1", newComment(added1.id, "changed"))
        val newComm = commentService.getComment(updated.id)
        assertThat(newComm?.entryId).isEqualTo("e1")
        assertThat(newComm?.plainText).isEqualTo("changed")

        val oldComm = commentService.getComment(added1.id)
        assertThat(oldComm?.entryId).isEqualTo("e1")
        assertThat(oldComm?.plainText).isEqualTo("changed")
    }

    @Test
    fun testUpdateCommentNoId() {
        val added1 = commentService.addComment("e1", newComment(content = "comment content 1"))
        assertThat(commentService.getComment(added1.id)?.entryId).isEqualTo("e1")

        val updated = commentService.updateComment("e1", newComment(content = "new comment"))
        assertThat(commentService.getComment(updated.id)?.entryId).isEqualTo("e1")
        assertThat(added1.id).isNotEqualTo(updated.id)

        val comments = commentService.getCommentsFor("e1", PageRequest())
        assertThat(comments).hasSize(2)
        assertThat(comments).extracting("id").containsOnly(added1.id, updated.id)
    }

    private fun newComment(id: String? = null, content: String) = NewComment(id, content)

}