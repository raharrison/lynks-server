package common

import comment.CommentService
import entry.LinkService
import group.CollectionService
import group.TagService
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import resource.ResourceManager
import resource.ResourceType
import schedule.ScheduleService
import schedule.ScheduleType
import util.*

class CascadingDeleteTest: DatabaseTest() {

    private val tagService = TagService()
    private val collectionService = CollectionService()
    private val resourceManager = ResourceManager()
    private val commentService = CommentService()
    private val scheduleService = ScheduleService()
    private lateinit var linkService: LinkService

    @BeforeEach
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyCollection("c1", "col1")
        createDummyCollection("c2", "col2", "c1")
        createDummyEntry("id1", "link1", "link content", EntryType.LINK)
        createDummyComment("c1", "id1", "comment content")
        createDummyReminder("rem1", "id1", ScheduleType.REMINDER, System.currentTimeMillis().toString())

        resourceManager.saveGeneratedResource("r1", "id1", "resource name", "jpg", ResourceType.SCREENSHOT, 11)

        linkService = LinkService(tagService, collectionService, resourceManager, mockk(relaxUnitFun = true))
        tagService.rebuild()
        collectionService.rebuild()
    }

    @Test
    fun testDeletingTagDoesntDeleteEntry() {
        val added = linkService.add(NewLink(null, "title", "url", listOf("t1"), emptyList(), false))
        assertThat(added.tags).hasSize(1)

        assertThat(tagService.delete("t1")).isTrue()

        val link = linkService.get(added.id)!!
        assertThat(link.tags).isEmpty()

        assertThat(tagService.get("t1")).isNull()
    }

    @Test
    fun testDeletingCollectionDoesntDeleteEntry() {
        val added = linkService.add(NewLink(null, "title", "url", emptyList(), listOf("c2"), false))
        assertThat(added.collections).hasSize(1)

        assertThat(collectionService.delete("c1")).isTrue()

        val link = linkService.get(added.id)!!
        assertThat(link.collections).isEmpty()

        assertThat(collectionService.get("c2")).isNull()
        assertThat(collectionService.get("c1")).isNull()
    }

    @Test
    fun testDeletingEntryDoesntDeleteTag() {
        val added = linkService.add(NewLink(null, "title", "url", listOf("t1"), emptyList(), false))

        assertThat(tagService.getAll()).hasSize(1)

        assertThat(linkService.delete(added.id)).isTrue()
        assertThat(tagService.getAll()).hasSize(1)
    }

    @Test
    fun testDeletingEntryDoesntDeleteCollection() {
        val added = linkService.add(NewLink(null, "title", "url", emptyList(), listOf("c2"), false))

        assertThat(collectionService.getAll()).hasSize(1)

        assertThat(linkService.delete(added.id)).isTrue()
        assertThat(collectionService.getAll()).hasSize(1)
    }

    @Test
    fun testDeletingCommentDoesntDeleteEntry() {
        assertThat(commentService.getCommentsFor("id1")).hasSize(1)
        assertThat(commentService.deleteComment("id1", "c1")).isTrue()
        assertThat(commentService.getCommentsFor("id1")).isEmpty()
        assertThat(linkService.get("id1")).isNotNull
    }

    @Test
    fun testDeletingEntryDeletesComments() {
        assertThat(commentService.getCommentsFor("id1")).hasSize(1)
        assertThat(linkService.delete("id1")).isTrue()
        assertThat(commentService.getCommentsFor("id1")).isEmpty()
        assertThat(linkService.get("id1")).isNull()
    }

    @Test
    fun testDeletingResourceDoesntDeleteEntry() {
        assertThat(resourceManager.getResourcesFor("id1")).hasSize(1)
        assertThat(resourceManager.delete("r1")).isTrue()
        assertThat(resourceManager.getResourcesFor("id1")).isEmpty()
        assertThat(linkService.get("id1")).isNotNull
    }

    @Test
    fun testDeletingEntryDeletesResources() {
        assertThat(resourceManager.getResourcesFor("id1")).hasSize(1)
        assertThat(linkService.delete("id1")).isTrue()
        assertThat(resourceManager.getResourcesFor("id1")).isEmpty()
        assertThat(linkService.get("id1")).isNull()
    }

    @Test
    fun testDeletingScheduleDoesntDeleteEntry() {
        assertThat(scheduleService.getRemindersForEntry("id1")).hasSize(1)
        assertThat(scheduleService.delete("rem1")).isTrue()
        assertThat(scheduleService.getRemindersForEntry("id1")).isEmpty()
        assertThat(linkService.get("id1")).isNotNull
    }

    @Test
    fun testDeletingEntryDeletesSchedules() {
        assertThat(scheduleService.getRemindersForEntry("id1")).hasSize(1)
        assertThat(linkService.delete("id1")).isTrue()
        assertThat(scheduleService.getRemindersForEntry("id1")).isEmpty()
        assertThat(linkService.get("id1")).isNull()
    }

    @Test
    fun testDeleteAllDoesntDeleteEntry() {
        assertThat(commentService.deleteComment("id1", "c1")).isTrue()
        assertThat(tagService.delete("t1")).isTrue()
        assertThat(collectionService.delete("c1")).isTrue()
        assertThat(scheduleService.delete("rem1")).isTrue()
        assertThat(resourceManager.delete("r1")).isTrue()

        assertThat(linkService.get("id1")).isNotNull
    }
}