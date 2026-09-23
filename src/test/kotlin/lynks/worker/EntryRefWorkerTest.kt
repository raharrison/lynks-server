package lynks.worker

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import lynks.comment.Comment
import lynks.comment.CommentService
import lynks.common.CommentId
import lynks.common.EntryId
import lynks.common.Note
import lynks.common.SlimNote
import lynks.common.page.DefaultPageRequest
import lynks.common.page.Page
import lynks.entry.EntryService
import lynks.entry.ref.EntryRefService
import lynks.util.TEST_USER
import lynks.util.markdown.MarkdownProcessor
import org.junit.jupiter.api.Test
import java.time.Instant

@ExperimentalCoroutinesApi
class EntryRefWorkerTest {

    private val entryRefService = mockk<EntryRefService>(relaxUnitFun = true)
    private val entryService = mockk<EntryService>()
    private val commentService = mockk<CommentService>()
    private val markdownProcessor = MarkdownProcessor(mockk(), entryService)

    private val note1 = Note(EntryId("id1"), "title1", "one @id2 two @id3 three @id4 end", "content1", Instant.EPOCH, Instant.EPOCH)
    private val comment = Comment(CommentId("cid1"), EntryId("id1"), "one @id2 two @id3 three @id4 end", "content1", Instant.EPOCH, Instant.EPOCH)
    private val slimNotes = listOf(SlimNote(EntryId("id2"), "title2", Instant.EPOCH), SlimNote(EntryId("id3"), "title3", Instant.EPOCH))

    @Test
    fun testSetEntryRefsFromEntry(): Unit = runTest {
        every { entryService.get(TEST_USER, EntryId("id1")) } returns note1
        every { entryService.get(TEST_USER, listOf(EntryId("id2"), EntryId("id3"), EntryId("id4")), any()) } returns Page.of(
            slimNotes,
            DefaultPageRequest,
            2
        )
        val entryRefWorker = EntryRefWorker(markdownProcessor, entryRefService, entryService, commentService)
            .apply { runner = this@runTest.coroutineContext }.worker()

        val request = DefaultEntryRefWorkerRequest(TEST_USER, EntryId("id1"))
        entryRefWorker.send(request)
        advanceUntilIdle()
        entryRefWorker.close()

        coVerify(exactly = 1) { entryService.get(TEST_USER, EntryId("id1")) }
        coVerify(exactly = 1) { entryService.get(TEST_USER, listOf(EntryId("id2"), EntryId("id3"), EntryId("id4")), any()) }
        coVerify(exactly = 1) { entryRefService.setEntryRefs(EntryId("id1"), listOf("id2", "id3"), "id1") }
    }

    @Test
    fun testEntryNotFound() = runTest {
        every { entryService.get(TEST_USER, EntryId("id1")) } returns null
        val entryRefWorker = EntryRefWorker(markdownProcessor, entryRefService, entryService, commentService)
            .apply { runner = this@runTest.coroutineContext }.worker()

        val request = DefaultEntryRefWorkerRequest(TEST_USER, EntryId("id1"))
        entryRefWorker.send(request)
        advanceUntilIdle()
        entryRefWorker.close()

        coVerify(exactly = 1) { entryService.get(TEST_USER, EntryId("id1")) }
        coVerify(exactly = 0) { entryRefService.setEntryRefs(any(), any(), any()) }
    }

    @Test
    fun testSetEntryRefsFromNewComment() = runTest {
        every { commentService.getComment(TEST_USER, EntryId("id1"), CommentId("cid1")) } returns comment
        every { entryService.get(TEST_USER, listOf(EntryId("id2"), EntryId("id3"), EntryId("id4")), any()) } returns Page.of(
            slimNotes,
            DefaultPageRequest,
            2
        )
        val entryRefWorker = EntryRefWorker(markdownProcessor, entryRefService, entryService, commentService)
            .apply { runner = this@runTest.coroutineContext }.worker()

        val request = CommentRefWorkerRequest(TEST_USER, EntryId("id1"), CommentId("cid1"), CrudType.CREATE)
        entryRefWorker.send(request)
        advanceUntilIdle()
        entryRefWorker.close()

        coVerify(exactly = 1) { commentService.getComment(TEST_USER, EntryId("id1"), CommentId("cid1")) }
        coVerify(exactly = 1) { entryService.get(TEST_USER, listOf(EntryId("id2"), EntryId("id3"), EntryId("id4")), any()) }
        coVerify(exactly = 1) { entryRefService.setEntryRefs(EntryId("id1"), listOf("id2", "id3"), "cid1") }
    }

    @Test
    fun testRemoveEntryRefsFromDeletedComment() = runTest {
        // the row is gone by the time the worker runs
        every { commentService.getComment(TEST_USER, EntryId("id1"), CommentId("cid1")) } returns null
        every { entryRefService.deleteOrigin("cid1") } returns 1
        val entryRefWorker = EntryRefWorker(markdownProcessor, entryRefService, entryService, commentService)
            .apply { runner = this@runTest.coroutineContext }.worker()

        val request = CommentRefWorkerRequest(TEST_USER, EntryId("id1"), CommentId("cid1"), CrudType.DELETE)
        entryRefWorker.send(request)
        advanceUntilIdle()
        entryRefWorker.close()

        coVerify(exactly = 1) { entryRefService.deleteOrigin("cid1") }
        coVerify(exactly = 0) { entryRefService.setEntryRefs(any(), any(), any()) }
    }

    @Test
    fun testCommentNotFound() = runTest {
        every { commentService.getComment(TEST_USER, EntryId("id1"), CommentId("cid1")) } returns null
        val entryRefWorker = EntryRefWorker(markdownProcessor, entryRefService, entryService, commentService)
            .apply { runner = this@runTest.coroutineContext }.worker()

        val request = CommentRefWorkerRequest(TEST_USER, EntryId("id1"), CommentId("cid1"), CrudType.CREATE)
        entryRefWorker.send(request)
        advanceUntilIdle()
        entryRefWorker.close()

        coVerify(exactly = 1) { commentService.getComment(TEST_USER, EntryId("id1"), CommentId("cid1")) }
        coVerify(exactly = 0) { entryRefService.setEntryRefs(any(), any(), any()) }
    }

}
