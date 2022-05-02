package lynks.worker

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import lynks.comment.Comment
import lynks.comment.CommentService
import lynks.common.Note
import lynks.common.SlimNote
import lynks.common.page.DefaultPageRequest
import lynks.common.page.Page
import lynks.entry.EntryService
import lynks.entry.ref.EntryRefService
import lynks.util.markdown.MarkdownProcessor
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class EntryRefWorkerTest {

    private val markdownProcessor = MarkdownProcessor(mockk())
    private val entryRefService = mockk<EntryRefService>(relaxUnitFun = true)
    private val entryService = mockk<EntryService>()
    private val commentService = mockk<CommentService>()

    private val note1 = Note("id1", "title1", "one @id2 two @id3 three @id4 end", "content1", 123L, 123L)
    private val comment = Comment("cid1", "id1", "one @id2 two @id3 three @id4 end", "content1", 123L, 123L)
    private val slimNotes = listOf(SlimNote("id2", "title2", 123L), SlimNote("id3", "title3", 123L))

    @Test
    fun testSetEntryRefsFromEntry(): Unit = runTest {
        every { entryService.get("id1") } returns note1
        every { entryService.get(listOf("id2", "id3", "id4"), any()) } returns Page.of(slimNotes, DefaultPageRequest, 2)
        val entryRefWorker = EntryRefWorker(markdownProcessor, entryRefService, entryService, commentService)
            .apply { runner = this@runTest.coroutineContext }.worker()

        val request = DefaultEntryRefWorkerRequest("id1")
        entryRefWorker.send(request)
        advanceUntilIdle()
        entryRefWorker.close()

        coVerify(exactly = 1) { entryService.get("id1") }
        coVerify(exactly = 1) { entryService.get(listOf("id2", "id3", "id4"), any()) }
        coVerify(exactly = 1) { entryRefService.setEntryRefs("id1", listOf("id2", "id3"), "id1") }
    }

    @Test
    fun testEntryNotFound() = runTest {
        every { entryService.get("id1") } returns null
        val entryRefWorker = EntryRefWorker(markdownProcessor, entryRefService, entryService, commentService)
            .apply { runner = this@runTest.coroutineContext }.worker()

        val request = DefaultEntryRefWorkerRequest("id1")
        entryRefWorker.send(request)
        advanceUntilIdle()
        entryRefWorker.close()

        coVerify(exactly = 1) { entryService.get("id1") }
        coVerify(exactly = 0) { entryRefService.setEntryRefs(any(), any(), any()) }
    }

    @Test
    fun testSetEntryRefsFromNewComment() = runTest {
        every { commentService.getComment("id1", "cid1") } returns comment
        every { entryService.get(listOf("id2", "id3", "id4"), any()) } returns Page.of(slimNotes, DefaultPageRequest, 2)
        val entryRefWorker = EntryRefWorker(markdownProcessor, entryRefService, entryService, commentService)
            .apply { runner = this@runTest.coroutineContext }.worker()

        val request = CommentRefWorkerRequest("id1", "cid1", CrudType.CREATE)
        entryRefWorker.send(request)
        advanceUntilIdle()
        entryRefWorker.close()

        coVerify(exactly = 1) { commentService.getComment("id1", "cid1") }
        coVerify(exactly = 1) { entryService.get(listOf("id2", "id3", "id4"), any()) }
        coVerify(exactly = 1) { entryRefService.setEntryRefs("id1", listOf("id2", "id3"), "cid1") }
    }

    @Test
    fun testSetEntryRefsFromDeletedComment() = runTest {
        every { commentService.getComment("id1", "cid1") } returns comment
        every { entryService.get(listOf("id2", "id3", "id4"), any()) } returns Page.of(slimNotes, DefaultPageRequest, 2)
        every { entryRefService.deleteOrigin("cid1") } returns 1
        val entryRefWorker = EntryRefWorker(markdownProcessor, entryRefService, entryService, commentService)
            .apply { runner = this@runTest.coroutineContext }.worker()

        val request = CommentRefWorkerRequest("id1", "cid1", CrudType.DELETE)
        entryRefWorker.send(request)
        advanceUntilIdle()
        entryRefWorker.close()

        coVerify(exactly = 1) { commentService.getComment("id1", "cid1") }
        coVerify(exactly = 1) { entryService.get(listOf("id2", "id3", "id4"), any()) }
        coVerify(exactly = 1) { entryRefService.setEntryRefs("id1", listOf("id2", "id3"), "cid1") }
        coVerify(exactly = 1) { entryRefService.deleteOrigin("cid1") }
    }

    @Test
    fun testCommentNotFound() = runTest {
        every { commentService.getComment("id1", "cid1") } returns null
        val entryRefWorker = EntryRefWorker(markdownProcessor, entryRefService, entryService, commentService)
            .apply { runner = this@runTest.coroutineContext }.worker()

        val request = CommentRefWorkerRequest("id1", "cid1", CrudType.CREATE)
        entryRefWorker.send(request)
        advanceUntilIdle()
        entryRefWorker.close()

        coVerify(exactly = 1) { commentService.getComment("id1", "cid1") }
        coVerify(exactly = 0) { entryRefService.setEntryRefs(any(), any(), any()) }
    }

}
