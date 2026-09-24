package lynks.user

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import lynks.comment.CommentService
import lynks.comment.NewComment
import lynks.common.*
import lynks.common.exception.InvalidModelException
import lynks.common.page.PageRequest
import lynks.digest.DigestService
import lynks.entry.EntryAuditService
import lynks.entry.EntryService
import lynks.entry.LinkService
import lynks.entry.NoteService
import lynks.entry.ref.EntryRefService
import lynks.group.*
import lynks.notify.NewNotification
import lynks.notify.NotificationMethod
import lynks.notify.NotifyService
import lynks.reminder.NewReminder
import lynks.reminder.ReminderService
import lynks.reminder.ReminderStatus
import lynks.reminder.ReminderType
import lynks.resource.*
import lynks.util.*
import lynks.util.markdown.MarkdownProcessor
import lynks.worker.WorkerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Paths
import java.time.ZoneId

// Every service is exercised as two users, checking neither can see or change the other's data
class UserIsolationTest : DatabaseTest() {

    private val workerRegistry = mockk<WorkerRegistry>(relaxUnitFun = true)
    private val tagService = TagService()
    private val collectionService = CollectionService()
    private val groupSetService = GroupSetService(tagService, collectionService)
    private val resourceManager = ResourceManager(FileStore(), ResourceRepository())
    private val entryAuditService = EntryAuditService()
    private val entryService = EntryService(groupSetService, entryAuditService, resourceManager)
    private val markdownProcessor = MarkdownProcessor(resourceManager, entryService)
    private val linkService = LinkService(groupSetService, entryAuditService, resourceManager, workerRegistry)
    private val noteService = NoteService(groupSetService, entryAuditService, resourceManager, workerRegistry, markdownProcessor)
    private val commentService = CommentService(workerRegistry, markdownProcessor)
    private val reminderService = ReminderService(workerRegistry)
    private val notifyService = NotifyService(mockk(), UserService(mockk()))
    private val entryRefService = EntryRefService()
    private val digestService = DigestService(linkService)

    @BeforeEach
    fun createOtherUser() {
        createDummyUser("other-user", id = OTHER_USER)
    }

    @AfterEach
    fun cleanUp() {
        Paths.get(Environment.resource.resourceBasePath).toFile().deleteRecursively()
    }

    private fun addNote(userId: UserId = TEST_USER, title: String = "secret plans", content: String = "hidden content") =
        noteService.add(userId, NewNote(null, title, content))

    @Test
    fun testEntriesAreInvisibleToOtherUsers() {
        val note = addNote()
        updateDummyEntry(note.id.value, note.title, 2)

        assertThat(noteService.get(OTHER_USER, note.id)).isNull()
        assertThat(entryService.get(OTHER_USER, note.id)).isNull()
        assertThat(noteService.get(OTHER_USER, note.id, 1)).isNull()
        assertThat(noteService.get(OTHER_USER).content).isEmpty()
        assertThat(entryService.get(OTHER_USER).content).isEmpty()
        assertThat(entryService.get(OTHER_USER, listOf(note.id)).content).isEmpty()
        assertThat(entryService.search(OTHER_USER, "secret").content).isEmpty()
        assertThat(entryService.suggest(OTHER_USER, "secret").content).isEmpty()
        assertThat(entryService.getEntryVersions(OTHER_USER, note.id)).isEmpty()
        assertThat(entryAuditService.getEntryAudit(OTHER_USER, note.id)).isEmpty()

        assertThat(noteService.get(TEST_USER, note.id)).isNotNull
        assertThat(entryService.get(TEST_USER).content).hasSize(1)
        assertThat(entryService.search(TEST_USER, "secret").content).hasSize(1)
        assertThat(entryService.suggest(TEST_USER, "secret").content).hasSize(1)
        assertThat(entryService.getEntryVersions(TEST_USER, note.id)).hasSize(2)
        assertThat(entryAuditService.getEntryAudit(TEST_USER, note.id)).isNotEmpty
    }

    @Test
    fun testEntriesCannotBeChangedByOtherUsers() {
        val note = addNote()
        createDummyTag("ot1", "their tag", OTHER_USER)

        assertThat(noteService.update(OTHER_USER, NewNote(note.id, "taken", "overwritten"))).isNull()
        assertThat(entryService.star(OTHER_USER, note.id, true)).isNull()
        assertThat(noteService.revert(OTHER_USER, note.id, 1)).isNull()
        assertThat(entryService.updateEntryGroups(OTHER_USER, note.id, listOf("ot1"), emptyList())).isFalse()
        noteService.mergeProps(OTHER_USER, note.id, BaseProperties().apply { addAttribute("injected", true) })
        assertThat(noteService.delete(OTHER_USER, note.id)).isFalse()

        val unchanged = noteService.get(TEST_USER, note.id)!!
        assertThat(unchanged.title).isEqualTo("secret plans")
        assertThat(unchanged.plainContent).isEqualTo("hidden content")
        assertThat(unchanged.version).isEqualTo(1)
        assertThat(unchanged.starred).isFalse()
        assertThat(unchanged.tags).isEmpty()
        assertThat(unchanged.props.containsAttribute("injected")).isFalse()
    }

    @Test
    fun testLinksAreInvisibleToOtherUsers() {
        val link = linkService.add(TEST_USER, NewLink(null, "title", "https://example.com/page", process = false))

        assertThat(linkService.checkExistingWithUrl(OTHER_USER, "https://example.com/page")).isEmpty()
        assertThat(linkService.getUnread(OTHER_USER)).isEmpty()
        assertThat(linkService.getSlim(OTHER_USER, listOf(link.id))).isEmpty()
        assertThat(linkService.read(OTHER_USER, link.id, true)).isNull()
        assertThat(linkService.updateSearchableContent(OTHER_USER, link.id, "words")).isNull()

        assertThat(linkService.checkExistingWithUrl(TEST_USER, "https://example.com/page")).hasSize(1)
        assertThat(linkService.getUnread(TEST_USER)).hasSize(1)
    }

    @Test
    fun testEntriesCannotUseAnotherUsersGroups() {
        createDummyTag("ot1", "their tag", OTHER_USER)
        createDummyCollection("oc1", "their collection", userId = OTHER_USER)

        assertThrows<InvalidModelException> { noteService.add(TEST_USER, NewNote(null, "title", "content", listOf("ot1"))) }
        assertThrows<InvalidModelException> {
            noteService.add(TEST_USER, NewNote(null, "title", "content", collections = listOf("oc1")))
        }
        assertThat(noteService.get(TEST_USER).content).isEmpty()
    }

    @Test
    fun testGroupsArePerUser() {
        val mine = tagService.add(TEST_USER, NewTag(name = "reading"))
        val theirs = tagService.add(OTHER_USER, NewTag(name = "reading"))
        assertThat(mine.id).isNotEqualTo(theirs.id)

        assertThat(tagService.getAll(TEST_USER)).extracting<String> { it.id }.containsExactly(mine.id)
        assertThat(tagService.getAll(OTHER_USER)).extracting<String> { it.id }.containsExactly(theirs.id)
        assertThat(tagService.get(OTHER_USER, mine.id)).isNull()
        assertThat(tagService.update(OTHER_USER, NewTag(mine.id, "renamed"))).isNull()
        assertThat(tagService.delete(OTHER_USER, mine.id)).isFalse()
        assertThat(tagService.get(TEST_USER, mine.id)?.name).isEqualTo("reading")

        // path creation only matches the caller's own collections
        val nested = collectionService.add(TEST_USER, NewCollection(name = "work/projects"))
        val otherNested = collectionService.add(OTHER_USER, NewCollection(name = "work/projects"))
        assertThat(nested.id).isNotEqualTo(otherNested.id)
        assertThat(collectionService.getFromPath(TEST_USER, "work")).isNotNull
        assertThat(collectionService.getAll(OTHER_USER)).hasSize(1)
        assertThat(collectionService.getAll(TEST_USER)).hasSize(1)
    }

    @Test
    fun testCollectionParentMustBelongToUser() {
        val theirs = collectionService.add(OTHER_USER, NewCollection(name = "theirs"))
        assertThrows<InvalidModelException> {
            collectionService.add(TEST_USER, NewCollection(name = "mine", parentId = theirs.id))
        }
        val mine = collectionService.add(TEST_USER, NewCollection(name = "mine"))
        assertThrows<InvalidModelException> {
            collectionService.update(TEST_USER, NewCollection(mine.id, "mine", theirs.id))
        }
        assertThat(collectionService.get(OTHER_USER, theirs.id)?.children).isEmpty()
    }

    @Test
    fun testSuggestionsOnlyMatchOwnGroups() {
        tagService.add(OTHER_USER, NewTag(name = "kotlin"))
        assertThat(groupSetService.matchWithContent(TEST_USER, "a post about kotlin").tags).isEmpty()
        assertThat(groupSetService.matchWithContent(OTHER_USER, "a post about kotlin").tags).hasSize(1)
    }

    @Test
    fun testCommentsAreScopedToEntryOwner() {
        val note = addNote()
        val comment = commentService.addComment(TEST_USER, note.id, NewComment(null, "my comment"))!!

        assertThat(commentService.getCommentsFor(OTHER_USER, note.id).content).isEmpty()
        assertThat(commentService.getComment(OTHER_USER, note.id, comment.id)).isNull()
        assertThat(commentService.addComment(OTHER_USER, note.id, NewComment(null, "intruder"))).isNull()
        assertThat(commentService.updateComment(OTHER_USER, note.id, NewComment(comment.id, "changed"))).isNull()
        assertThat(commentService.deleteComment(OTHER_USER, note.id, comment.id)).isFalse()

        val comments = commentService.getCommentsFor(TEST_USER, note.id).content
        assertThat(comments).extracting<String> { it.plainContent }.containsExactly("my comment")
    }

    @Test
    fun testRemindersAreScopedToEntryOwner() {
        val note = addNote()
        val tz = ZoneId.systemDefault().id
        val reminder = reminderService.addReminder(
            TEST_USER,
            NewReminder(
                null,
                note.id,
                ReminderType.ADHOC,
                listOf(NotificationMethod.PUSH),
                "mine",
                100,
                null,
                tz,
                ReminderStatus.ACTIVE
            )
        )

        assertThat(reminderService.getAllReminders(OTHER_USER).content).isEmpty()
        assertThat(reminderService.get(OTHER_USER, reminder.reminderId)).isNull()
        assertThat(reminderService.getRemindersForEntry(OTHER_USER, note.id)).isEmpty()
        assertThrows<InvalidModelException> {
            reminderService.addReminder(
                OTHER_USER,
                NewReminder(
                    null,
                    note.id,
                    ReminderType.ADHOC,
                    listOf(NotificationMethod.PUSH),
                    "theirs",
                    100,
                    null,
                    tz,
                    ReminderStatus.ACTIVE
                )
            )
        }
        assertThat(
            reminderService.updateReminder(
                OTHER_USER,
                NewReminder(
                    reminder.reminderId,
                    note.id,
                    ReminderType.ADHOC,
                    listOf(NotificationMethod.PUSH),
                    "changed",
                    100,
                    null,
                    tz,
                    ReminderStatus.DISABLED
                )
            )
        ).isNull()
        assertThat(reminderService.delete(OTHER_USER, reminder.reminderId)).isFalse()

        assertThat(reminderService.get(TEST_USER, reminder.reminderId)?.message).isEqualTo("mine")
        assertThat(reminderService.getAllActiveReminders()).containsExactly(TEST_USER to reminder)
    }

    @Test
    fun testNotificationsArePerUser() = runBlocking {
        val mine = notifyService.create(TEST_USER, NewNotification.processed("mine"))
        notifyService.create(OTHER_USER, NewNotification.processed("theirs"))

        assertThat(notifyService.getNotifications(TEST_USER).content).extracting<String> { it.message }.containsExactly("mine")
        assertThat(notifyService.getNotifications(OTHER_USER).content).extracting<String> { it.message }.containsExactly("theirs")
        assertThat(notifyService.getNotification(OTHER_USER, mine.id)).isNull()
        assertThat(notifyService.read(OTHER_USER, mine.id, true)).isZero()

        assertThat(notifyService.markAllRead(OTHER_USER)).isOne()
        assertThat(notifyService.getUnreadCount(OTHER_USER)).isZero()
        assertThat(notifyService.getUnreadCount(TEST_USER)).isOne()
    }

    @Test
    fun testResourcesAreScopedToEntryOwner() {
        val note = addNote()
        val resource = resourceManager.saveGeneratedResource(note.id, "file.txt", ResourceType.UPLOAD, byteArrayOf(1, 2, 3))

        assertThat(resourceManager.getResourcesFor(OTHER_USER, note.id)).isEmpty()
        assertThat(resourceManager.getResource(OTHER_USER, note.id, resource.id)).isNull()
        assertThat(resourceManager.getResourceAsFile(OTHER_USER, note.id, resource.id)).isNull()
        assertThat(resourceManager.updateResource(OTHER_USER, note.id, resource.copy(name = "renamed.txt"))).isNull()
        assertThat(resourceManager.delete(OTHER_USER, note.id, resource.id)).isFalse()

        // a resource is only found through the entry it belongs to
        val otherNote = addNote()
        assertThat(resourceManager.getResource(TEST_USER, otherNote.id, resource.id)).isNull()

        assertThat(resourceManager.getResource(TEST_USER, note.id, resource.id)?.name).isEqualTo("file.txt")
        assertThat(resourceManager.getResourceAsFile(TEST_USER, note.id, resource.id)!!.second.readBytes())
            .isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun testTempUploadsArePerUser() {
        val upload = resourceManager.saveTempUpload(TEST_USER, byteArrayOf(1, 2, 3), PNG)
        val name = upload.fileName.toString()
        assertThat(resourceManager.findTempUpload(TEST_USER, name)).isEqualTo(upload)
        assertThat(resourceManager.findTempUpload(OTHER_USER, name)).isNull()

        // another user pasting the same url neither claims nor moves the file
        val note = addNote(OTHER_USER, content = "![img]($TEMP_UPLOAD_URL$name)")
        assertThat(resourceManager.getResourcesFor(OTHER_USER, note.id)).isEmpty()
        assertThat(Files.exists(upload)).isTrue()
    }

    @Test
    fun testMentionsOnlyResolveOwnEntries() {
        val secret = addNote(title = "Secret title")
        val theirs = addNote(OTHER_USER, title = "theirs", content = "see @${secret.id}")
        assertThat(theirs.renderedContent).doesNotContain("Secret title").doesNotContain("href")

        val mine = addNote(title = "mine", content = "see @${secret.id}")
        assertThat(mine.renderedContent).contains("Secret title").contains("href=\"/notes/${secret.id}\"")
    }

    @Test
    fun testEntryRefsAreScopedToOwner() {
        val source = addNote()
        val target = addNote()
        createDummyEntryRef(source.id.value, target.id.value, source.id.value)

        assertThat(entryRefService.getRefsForEntry(TEST_USER, target.id).inbound).hasSize(1)
        assertThat(entryRefService.getRefsForEntry(OTHER_USER, target.id).inbound).isEmpty()
        assertThat(entryRefService.getRefsForEntry(OTHER_USER, source.id).outbound).isEmpty()
    }

    @Test
    fun testDigestsArePerUser() {
        linkService.add(TEST_USER, NewLink(null, "unread", "https://example.com", process = false))

        assertThat(digestService.generate(OTHER_USER)).isNull()
        val digest = digestService.generate(TEST_USER)!!
        assertThat(digest.links).hasSize(1)
        assertThat(digestService.getLatest(OTHER_USER)).isNull()
        assertThat(digestService.get(OTHER_USER, digest.id)).isNull()
        assertThat(digestService.getLatest(TEST_USER)?.id).isEqualTo(digest.id)
    }

    @Test
    fun testPagingCountsOnlyOwnEntries() {
        repeat(3) { addNote() }
        repeat(2) { addNote(OTHER_USER) }
        assertThat(entryService.get(TEST_USER, PageRequest(size = 1)).total).isEqualTo(3)
        assertThat(entryService.get(OTHER_USER, PageRequest(size = 1)).total).isEqualTo(2)
    }
}
