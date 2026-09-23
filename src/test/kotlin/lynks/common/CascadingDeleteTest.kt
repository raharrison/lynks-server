package lynks.common

import io.mockk.mockk
import lynks.comment.CommentService
import lynks.comment.Comments
import lynks.digest.Digests
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.entry.ref.EntryRefService
import lynks.entry.ref.EntryRefs
import lynks.group.*
import lynks.notify.NotificationMethod
import lynks.notify.NotificationType
import lynks.notify.Notifications
import lynks.reminder.ReminderService
import lynks.reminder.ReminderType
import lynks.reminder.Reminders
import lynks.resource.*
import lynks.user.Users
import lynks.util.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class CascadingDeleteTest: DatabaseTest() {

    private val tagService = TagService()
    private val collectionService = CollectionService()
    private val resourceManager = ResourceManager(FileStore(), ResourceRepository())
    private val commentService = CommentService(mockk(relaxUnitFun = true), mockk())
    private val reminderService = ReminderService(mockk(relaxUnitFun = true))
    private val entryAuditService = EntryAuditService()
    private val entryRefService = EntryRefService()
    private lateinit var linkService: LinkService

    @BeforeEach
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyCollection("c1", "col1")
        createDummyCollection("c2", "col2", "c1")
        createDummyEntry("id1", "link1", "link content", EntryType.LINK)
        createDummyComment("c1", "id1", "comment content")
        createDummyReminder(
            "rem1",
            "id1",
            ReminderType.ADHOC,
            listOf(NotificationMethod.JOLT),
            "message",
            System.currentTimeMillis().toString()
        )

        resourceManager.saveGeneratedResource(ResourceId("r1"), EntryId("id1"), "resource name", "jpg", ResourceType.SCREENSHOT, 11)
        updateDummyEntry("id1", "link1", 1, "r1") // add generated thumbnail resource id to entry

        linkService = LinkService(GroupSetService(tagService, collectionService), entryAuditService, resourceManager, mockk(relaxUnitFun = true))
        tagService.rebuild(TEST_USER)
        collectionService.rebuild(TEST_USER)
    }

    @Test
    fun testDeletingTagDoesntDeleteEntry() {
        val added = linkService.add(TEST_USER, NewLink(null, "title", "url", listOf("t1"), emptyList(), false))
        assertThat(added.tags).hasSize(1)

        assertThat(tagService.delete(TEST_USER, "t1")).isTrue()

        val link = linkService.get(TEST_USER, added.id)!!
        assertThat(link.tags).isEmpty()

        assertThat(tagService.get(TEST_USER, "t1")).isNull()
    }

    @Test
    fun testDeletingCollectionDoesntDeleteEntry() {
        val added = linkService.add(TEST_USER, NewLink(null, "title", "url", emptyList(), listOf("c2"), false))
        assertThat(added.collections).hasSize(1)

        assertThat(collectionService.delete(TEST_USER, "c1")).isTrue()

        val link = linkService.get(TEST_USER, added.id)!!
        assertThat(link.collections).isEmpty()

        assertThat(collectionService.get(TEST_USER, "c2")).isNull()
        assertThat(collectionService.get(TEST_USER, "c1")).isNull()
    }

    @Test
    fun testDeletingEntryRefDoesntDeleteEntry() {
        val added1 = linkService.add(TEST_USER, NewLink(null, "title", "url", process = false))
        val added2 = linkService.add(TEST_USER, NewLink(null, "title2", "url2", process = false))
        entryRefService.setEntryRefs(added1.id, listOf(added2.id.value), added1.id.value)
        entryRefService.deleteOrigin(added1.id.value)
        assertThat(linkService.get(TEST_USER, added1.id)).isNotNull()
        assertThat(linkService.get(TEST_USER, added2.id)).isNotNull()
    }

    @Test
    fun testDeletingEntryDoesntDeleteTag() {
        val added = linkService.add(TEST_USER, NewLink(null, "title", "url", listOf("t1"), emptyList(), false))

        assertThat(tagService.getAll(TEST_USER)).hasSize(1)

        assertThat(linkService.delete(TEST_USER, added.id)).isTrue()
        assertThat(tagService.getAll(TEST_USER)).hasSize(1)
    }

    @Test
    fun testDeletingEntryDoesntDeleteCollection() {
        val added = linkService.add(TEST_USER, NewLink(null, "title", "url", emptyList(), listOf("c2"), false))

        assertThat(collectionService.getAll(TEST_USER)).hasSize(1)

        assertThat(linkService.delete(TEST_USER, added.id)).isTrue()
        assertThat(collectionService.getAll(TEST_USER)).hasSize(1)
    }

    @Test
    fun testDeletingCommentDoesntDeleteEntry() {
        assertThat(commentService.getCommentsFor(TEST_USER, EntryId("id1")).content).hasSize(1)
        assertThat(commentService.deleteComment(TEST_USER, EntryId("id1"), CommentId("c1"))).isTrue()
        assertThat(commentService.getCommentsFor(TEST_USER, EntryId("id1")).content).isEmpty()
        assertThat(linkService.get(TEST_USER, EntryId("id1"))).isNotNull
    }

    @Test
    fun testDeletingEntryDeletesComments() {
        assertThat(commentService.getCommentsFor(TEST_USER, EntryId("id1")).content).hasSize(1)
        assertThat(linkService.delete(TEST_USER, EntryId("id1"))).isTrue()
        assertThat(commentService.getCommentsFor(TEST_USER, EntryId("id1")).content).isEmpty()
        assertThat(linkService.get(TEST_USER, EntryId("id1"))).isNull()
    }

    @Test
    fun testDeletingResourceDoesntDeleteEntry() {
        assertThat(resourceManager.getResourcesFor(TEST_USER, EntryId("id1"))).hasSize(1)
        assertThat(resourceManager.delete(TEST_USER, EntryId("id1"), ResourceId("r1"))).isTrue()
        assertThat(resourceManager.getResourcesFor(TEST_USER, EntryId("id1"))).isEmpty()
        assertThat(linkService.get(TEST_USER, EntryId("id1"))).isNotNull
    }

    @Test
    fun testDeletingEntryDeletesResources() {
        assertThat(resourceManager.getResourcesFor(TEST_USER, EntryId("id1"))).hasSize(1)
        assertThat(linkService.delete(TEST_USER, EntryId("id1"))).isTrue()
        assertThat(resourceManager.getResourcesFor(TEST_USER, EntryId("id1"))).isEmpty()
        assertThat(linkService.get(TEST_USER, EntryId("id1"))).isNull()
    }

    @Test
    fun testDeletingScheduleDoesntDeleteEntry() {
        assertThat(reminderService.getRemindersForEntry(TEST_USER, EntryId("id1"))).hasSize(1)
        assertThat(reminderService.delete(TEST_USER, ReminderId("rem1"))).isTrue()
        assertThat(reminderService.getRemindersForEntry(TEST_USER, EntryId("id1"))).isEmpty()
        assertThat(linkService.get(TEST_USER, EntryId("id1"))).isNotNull
    }

    @Test
    fun testDeletingEntryDeletesSchedules() {
        assertThat(reminderService.getRemindersForEntry(TEST_USER, EntryId("id1"))).hasSize(1)
        assertThat(linkService.delete(TEST_USER, EntryId("id1"))).isTrue()
        assertThat(reminderService.getRemindersForEntry(TEST_USER, EntryId("id1"))).isEmpty()
        assertThat(linkService.get(TEST_USER, EntryId("id1"))).isNull()
    }

    @Test
    fun testDeletingEntryDeletesAudit() {
        val added = linkService.add(TEST_USER, NewLink(null, "title", "url", listOf("t1"), emptyList(), false))
        assertThat(entryAuditService.getEntryAudit(TEST_USER, added.id)).hasSize(1)
        assertThat(linkService.delete(TEST_USER, added.id)).isTrue()
        assertThat(entryAuditService.getEntryAudit(TEST_USER, added.id)).isEmpty()
        assertThat(linkService.get(TEST_USER, added.id)).isNull()
    }

    @Test
    fun testDeletingEntryDeletesRefs() {
        val added1 = linkService.add(TEST_USER, NewLink(null, "title", "url", process = false))
        val added2 = linkService.add(TEST_USER, NewLink(null, "title2", "url2", process = false))
        entryRefService.setEntryRefs(added1.id, listOf(added2.id.value), added1.id.value)
        assertThat(entryRefService.getRefsForEntry(TEST_USER, added1.id).outbound).hasSize(1)
        assertThat(entryRefService.getRefsForEntry(TEST_USER, added2.id).inbound).hasSize(1)
        linkService.delete(TEST_USER, added1.id)
        assertThat(entryRefService.getRefsForEntry(TEST_USER, added1.id).outbound).isEmpty()
        assertThat(entryRefService.getRefsForEntry(TEST_USER, added2.id).inbound).isEmpty()
    }

    @Test
    fun testDeleteAllDoesntDeleteEntry() {
        assertThat(commentService.deleteComment(TEST_USER, EntryId("id1"), CommentId("c1"))).isTrue()
        assertThat(tagService.delete(TEST_USER, "t1")).isTrue()
        assertThat(collectionService.delete(TEST_USER, "c1")).isTrue()
        assertThat(reminderService.delete(TEST_USER, ReminderId("rem1"))).isTrue()
        assertThat(resourceManager.delete(TEST_USER, EntryId("id1"), ResourceId("r1"))).isTrue()

        assertThat(linkService.get(TEST_USER, EntryId("id1"))).isNotNull
    }

    @Test
    fun testDeletingResourceSetsThumbnailIdToNull() {
        assertThat(linkService.get(TEST_USER, EntryId("id1"))?.thumbnailId).isEqualTo(ResourceId("r1"))
        assertThat(resourceManager.delete(TEST_USER, EntryId("id1"), ResourceId("r1"))).isTrue()
        assertThat(resourceManager.getResourcesFor(TEST_USER, EntryId("id1"))).isEmpty()
        assertThat(linkService.get(TEST_USER, EntryId("id1"))?.thumbnailId).isNull()
    }

    @Test
    fun testDeletingEntryDeletesItsVersions() {
        updateDummyEntry("id1", "link1 updated", 2)
        assertThat(linkService.get(TEST_USER, EntryId("id1"), 2)).isNotNull
        assertThat(linkService.delete(TEST_USER, EntryId("id1"))).isTrue()
        assertThat(linkService.get(TEST_USER, EntryId("id1"), 1)).isNull()
        assertThat(linkService.get(TEST_USER, EntryId("id1"), 2)).isNull()
    }

    @Test
    fun testDeletingUserDeletesEverythingTheyOwn() {
        createDummyUser("other-user", id = OTHER_USER)
        createDummyEntry("o1", "other link", "content", EntryType.LINK, userId = OTHER_USER)
        createDummyEntry("o2", "other note", "content", EntryType.NOTE, userId = OTHER_USER)
        updateDummyEntry("o1", "other link updated", 2)
        createDummyTag("ot1", "other tag", OTHER_USER)
        createDummyCollection("oc1", "other col", userId = OTHER_USER)
        createDummyCollection("oc2", "other child", "oc1", OTHER_USER)
        createDummyComment("ocm1", "o1", "comment")
        createDummyReminder("orem1", "o1", ReminderType.ADHOC, listOf(NotificationMethod.PUSH), spec = "100")
        createDummyNotification("on1", NotificationType.PROCESSED, "message", "o1", OTHER_USER)
        createDummyNotification("on2", NotificationType.DIGEST, "message", null, OTHER_USER)
        createDummyEntryRef("o2", "o1", "o2")
        entryAuditService.acceptAuditEvent(EntryId("o1"), "source", "details")
        resourceManager.saveGeneratedResource(ResourceId("or1"), EntryId("o1"), "other", "jpg", ResourceType.SCREENSHOT, 1)
        transaction {
            Digests.insert {
                it[digestId] = "od1"
                it[userId] = OTHER_USER.value
                it[entryIds] = "o1"
                it[dateCreated] = OffsetDateTime.now(ZoneOffset.UTC)
            }
            EntryGroups.insert {
                it[groupId] = "ot1"
                it[entryId] = "o1"
            }
        }

        transaction { Users.deleteWhere { Users.id eq OTHER_USER.value } }

        transaction {
            assertThat(Entries.selectAll().where { Entries.userId eq OTHER_USER.value }.count()).isZero()
            assertThat(EntryVersions.selectAll().where { EntryVersions.userId eq OTHER_USER.value }.count()).isZero()
            assertThat(Groups.selectAll().where { Groups.userId eq OTHER_USER.value }.count()).isZero()
            assertThat(EntryGroups.selectAll().where { EntryGroups.groupId eq "ot1" }.count()).isZero()
            assertThat(Comments.selectAll().where { Comments.id eq "ocm1" }.count()).isZero()
            assertThat(Reminders.selectAll().where { Reminders.reminderId eq "orem1" }.count()).isZero()
            assertThat(Notifications.selectAll().where { Notifications.userId eq OTHER_USER.value }.count()).isZero()
            assertThat(Digests.selectAll().where { Digests.userId eq OTHER_USER.value }.count()).isZero()
            assertThat(EntryRefs.selectAll().where { EntryRefs.sourceEntryId eq "o2" }.count()).isZero()
            assertThat(EntryAudit.selectAll().where { EntryAudit.entryId eq "o1" }.count()).isZero()
            assertThat(Resources.selectAll().where { Resources.entryId eq "o1" }.count()).isZero()
            assertThat(ResourceVersions.selectAll().where { ResourceVersions.id eq "or1" }.count()).isZero()
        }

        // the remaining user's data is untouched
        assertThat(linkService.get(TEST_USER, EntryId("id1"))).isNotNull
        assertThat(commentService.getCommentsFor(TEST_USER, EntryId("id1")).content).hasSize(1)
        assertThat(reminderService.getRemindersForEntry(TEST_USER, EntryId("id1"))).hasSize(1)
        assertThat(resourceManager.getResourcesFor(TEST_USER, EntryId("id1"))).hasSize(1)
        assertThat(tagService.getAll(TEST_USER)).hasSize(1)
    }
}
