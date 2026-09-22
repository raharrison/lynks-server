package lynks.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import lynks.comment.CommentService
import lynks.comment.NewComment
import lynks.common.*
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.resource.PendingResource
import lynks.resource.ResourceManager
import lynks.util.createDummyEntry
import lynks.util.markdown.MarkdownProcessor
import lynks.worker.CrudType
import lynks.worker.WorkerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import java.sql.SQLException
import java.time.Instant

class CommentServiceTest : DatabaseTest() {

    private val resourceManager = mockk<ResourceManager>()
    private val workerRegistry = mockk<WorkerRegistry>(relaxUnitFun = true)
    private val commentService = CommentService(workerRegistry, MarkdownProcessor(resourceManager, mockk()))

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        Thread.sleep(10)// prevent having same creation timestamp
        createDummyEntry("e2", "title2", "content2", EntryType.NOTE)
    }

    @Test
    fun testCreateBasicComment() {
        val added = commentService.addComment(EntryId("e1"), newComment(content = "comment content"))
        assertThat(added.entryId).isEqualTo(EntryId("e1"))
        assertThat(added.plainContent).isEqualTo("comment content")
        assertThat(added.renderedContent).isEqualTo("<p>comment content</p>\n")
        assertThat(added.dateCreated).isAfter(Instant.EPOCH)
        assertThat(added.dateUpdated).isAfter(Instant.EPOCH)
    }

    @Test
    fun testCreateMarkdownComment() {
        val plain = "# header\n\na paragraph"
        val markdown = "<h1>header</h1>\n<p>a paragraph</p>\n"
        val added = commentService.addComment(EntryId("e1"), newComment(content = plain))
        assertThat(added.entryId).isEqualTo(EntryId("e1"))
        assertThat(added.plainContent).isEqualTo(plain)
        assertThat(added.renderedContent).isEqualTo(markdown)
        verify(exactly = 0) { resourceManager.attach(EntryId("e1"), any()) }
        verify { workerRegistry.acceptCommentRefWork(added.entryId, added.id, CrudType.CREATE) }
    }

    @Test
    fun testCreateCommentWithTempImage() {
        val plain = "something ![desc](${TEMP_UPLOAD_URL}one.png)"
        every { resourceManager.findTempUpload("one.png") } returns Path.of("one.png")
        val committed = slot<List<PendingResource>>()
        every { resourceManager.attach(EntryId("e1"), capture(committed)) } returns emptyList()
        val added = commentService.addComment(EntryId("e1"), newComment(content = plain))
        val reserved = committed.captured.single()
        assertThat(added.entryId).isEqualTo(EntryId("e1"))
        assertThat(added.plainContent.trim()).isEqualTo("something ![desc](${Environment.server.rootPath}/entry/e1/resource/${reserved.id})")
        verify(exactly = 1) { resourceManager.attach(EntryId("e1"), any()) }
        verify { workerRegistry.acceptCommentRefWork(added.entryId, added.id, CrudType.CREATE) }
    }

    @Test
    fun testEntryDoesntExist() {
        assertThrows<SQLException> { commentService.addComment(EntryId("invalid"), newComment(content = "comment content")) }
    }

    @Test
    fun testGetCommentById() {
        val added = commentService.addComment(EntryId("e1"), newComment(content = "comment content 1"))
        val retrieved = commentService.getComment(added.entryId, added.id)
        assertThat(retrieved).isNotNull
        assertThat(retrieved?.entryId).isEqualTo(EntryId("e1"))
        assertThat(retrieved?.plainContent).isEqualTo(added.plainContent)
        assertThat(retrieved?.dateCreated).isEqualTo(added.dateCreated)
        assertThat(retrieved?.dateUpdated).isEqualTo(added.dateUpdated)
        assertThat(retrieved?.id).isEqualTo(added.id)
        assertThat(retrieved?.renderedContent).isEqualTo(added.renderedContent)
    }

    @Test
    fun testGetCommentByIdDoesntExist() {
        assertThat(commentService.getComment(EntryId("invalid"), CommentId("invalid"))).isNull()
    }

    @Test
    fun testGetCommentsForEntry() {
        assertThat(commentService.getCommentsFor(EntryId("invalid")).content).isEmpty()

        commentService.addComment(EntryId("e1"), newComment(content = "comment content 1"))
        commentService.addComment(EntryId("e1"), newComment(content = "comment content 2"))

        val comments = commentService.getCommentsFor(EntryId("e1")).content
        assertThat(comments).hasSize(2)
        assertThat(comments).extracting<CommentId> { it.id }
            .doesNotHaveDuplicates()
        assertThat(comments).extracting<EntryId> { it.entryId}.containsOnly(EntryId("e1"))
        assertThat(comments).extracting("plainContent").contains("comment content 1")

        assertThat(commentService.getCommentsFor(EntryId("e2")).content).isEmpty()
    }

    @Test
    fun testGetCommentsPage() {
        commentService.addComment(EntryId("e1"), newComment(content = "comment content 1"))
        Thread.sleep(10)
        commentService.addComment(EntryId("e1"), newComment(content = "comment content 2"))
        Thread.sleep(10)
        commentService.addComment(EntryId("e1"), newComment(content = "comment content 3"))

        var comments = commentService.getCommentsFor(EntryId("e1"), PageRequest(1, 1))
        assertThat(comments.content).hasSize(1)
        assertThat(comments.page).isEqualTo(1L)
        assertThat(comments.size).isEqualTo(1)
        assertThat(comments.total).isEqualTo(3)
        assertThat(comments.content).extracting("plainContent").containsOnly("comment content 1")

        comments = commentService.getCommentsFor(EntryId("e1"), PageRequest(2, 1))
        assertThat(comments.content).hasSize(1)
        assertThat(comments.page).isEqualTo(2L)
        assertThat(comments.size).isEqualTo(1)
        assertThat(comments.total).isEqualTo(3)
        assertThat(comments.content).extracting("plainContent").containsOnly("comment content 2")

        comments = commentService.getCommentsFor(EntryId("e1"), PageRequest(1, 3))
        assertThat(comments.content).hasSize(3)
        assertThat(comments.page).isEqualTo(1L)
        assertThat(comments.size).isEqualTo(3)
        assertThat(comments.total).isEqualTo(3)

        comments = commentService.getCommentsFor(EntryId("e1"), PageRequest(1, 10))
        assertThat(comments.content).hasSize(3)
        assertThat(comments.page).isEqualTo(1L)
        assertThat(comments.size).isEqualTo(10)
        assertThat(comments.total).isEqualTo(3)
        assertThat(comments.content).extracting<CommentId> { it.id }
            .doesNotHaveDuplicates()
    }

    @Test
    fun testGetCommentsSorting() {
        val c1 = commentService.addComment(EntryId("e1"), newComment(content = "comment content 1"))
        Thread.sleep(10)
        val c2 = commentService.addComment(EntryId("e1"), newComment(content = "comment content 2"))
        Thread.sleep(10)
        val c3 = commentService.addComment(EntryId("e1"), newComment(content = "comment content 3"))

        var comments = commentService.getCommentsFor(EntryId("e1"), PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.DESC))
        assertThat(comments.content).extracting<CommentId> { it.id }
            .containsExactly(c3.id, c2.id, c1.id)
        assertThat(comments.page).isEqualTo(1L)
        assertThat(comments.size).isEqualTo(10)
        assertThat(comments.total).isEqualTo(3)

        comments = commentService.getCommentsFor(EntryId("e1"), PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.ASC))
        assertThat(comments.content).extracting<CommentId> { it.id }
            .containsExactly(c1.id, c2.id, c3.id)
        assertThat(comments.page).isEqualTo(1L)
        assertThat(comments.size).isEqualTo(10)
        assertThat(comments.total).isEqualTo(3)
    }

    @Test
    fun testDeleteComments() {
        assertThat(commentService.deleteComment(EntryId("invalid"), CommentId("invalid"))).isFalse()

        val added1 = commentService.addComment(EntryId("e1"), newComment(content = "comment content 1"))
        val added2 = commentService.addComment(EntryId("e1"), newComment(content = "comment content 2"))

        assertThat(commentService.deleteComment(EntryId("e1"), CommentId("e1"))).isFalse()
        assertThat(commentService.deleteComment(added1.entryId, added1.id)).isTrue()

        assertThat(commentService.getCommentsFor(EntryId("e1")).content).hasSize(1)
        assertThat(commentService.getComment(added1.entryId, added1.id)).isNull()

        assertThat(commentService.deleteComment(added2.entryId, added2.id)).isTrue()

        assertThat(commentService.getCommentsFor(EntryId("e1")).content).isEmpty()
        assertThat(commentService.getComment(added2.entryId, added2.id)).isNull()
        verify { workerRegistry.acceptCommentRefWork(added1.entryId, added1.id, CrudType.DELETE) }
        verify { workerRegistry.acceptCommentRefWork(added2.entryId, added2.id, CrudType.DELETE) }
    }

    @Test
    fun testUpdateExistingComment() {
        val added1 = commentService.addComment(EntryId("e1"), newComment(content = "comment content 1"))
        assertThat(commentService.getComment(added1.entryId, added1.id)?.entryId).isEqualTo(EntryId("e1"))
        assertThat(added1.dateCreated).isEqualTo(added1.dateUpdated)

        val updated = commentService.updateComment(EntryId("e1"), newComment(added1.id, "changed"))
        val newComm = commentService.getComment(updated!!.entryId, updated.id)
        assertThat(updated).isEqualTo(newComm)
        assertThat(newComm?.entryId).isEqualTo(EntryId("e1"))
        assertThat(newComm?.plainContent).isEqualTo("changed")
        assertThat(newComm?.dateCreated).isEqualTo(added1.dateCreated)
        assertThat(newComm?.dateUpdated).isNotEqualTo(added1.dateCreated)

        val oldComm = commentService.getComment(added1.entryId, added1.id)
        assertThat(oldComm?.entryId).isEqualTo(EntryId("e1"))
        assertThat(oldComm?.plainContent).isEqualTo("changed")
        assertThat(oldComm?.dateUpdated).isNotEqualTo(oldComm?.dateCreated)
        verify { workerRegistry.acceptCommentRefWork(added1.entryId, added1.id, CrudType.UPDATE) }
    }

    @Test
    fun testUpdateExistingCommentWithTempImage() {
        val added = commentService.addComment(EntryId("e1"), newComment(content = "comment content 1"))
        every { resourceManager.findTempUpload("one.png") } returns Path.of("one.png")
        val committed = slot<List<PendingResource>>()
        every { resourceManager.attach(EntryId("e1"), capture(committed)) } returns emptyList()
        val updated =
            commentService.updateComment(EntryId("e1"), newComment(added.id, "changed ![desc](${TEMP_UPLOAD_URL}one.png)"))
        val reserved = committed.captured.single()
        assertThat(updated?.entryId).isEqualTo(EntryId("e1"))
        assertThat(updated?.plainContent?.trim()).isEqualTo("changed ![desc](${Environment.server.rootPath}/entry/e1/resource/${reserved.id})")
        verify(exactly = 1) { resourceManager.attach(EntryId("e1"), any()) }
        verify { workerRegistry.acceptCommentRefWork(added.entryId, added.id, CrudType.UPDATE) }
    }

    @Test
    fun testUpdateCommentNoId() {
        val added1 = commentService.addComment(EntryId("e1"), newComment(content = "comment content 1"))
        assertThat(commentService.getComment(added1.entryId, added1.id)?.entryId).isEqualTo(EntryId("e1"))

        val updated = commentService.updateComment(EntryId("e1"), newComment(content = "new comment"))
        assertThat(commentService.getComment(updated!!.entryId, updated.id)?.entryId).isEqualTo(EntryId("e1"))
        assertThat(added1.id).isNotEqualTo(updated.id)
        assertThat(updated.dateCreated).isEqualTo(updated.dateUpdated)

        val comments = commentService.getCommentsFor(EntryId("e1")).content
        assertThat(comments).hasSize(2)
        assertThat(comments).extracting<CommentId> { it.id }
            .containsOnly(added1.id, updated.id)
        verify { workerRegistry.acceptCommentRefWork(added1.entryId, added1.id, CrudType.CREATE) }
    }

    private fun newComment(id: CommentId? = null, content: String) = NewComment(id, content)

}
