package lynks.service

import io.mockk.mockk
import lynks.common.DatabaseTest
import lynks.common.NewNote
import lynks.entry.NoteService
import lynks.group.CollectionService
import lynks.group.GroupSetService
import lynks.group.NewTag
import lynks.group.TagService
import lynks.util.TEST_USER
import lynks.util.createDummyTag
import lynks.util.markdown.MarkdownProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TagServiceTest : DatabaseTest() {

    private val tagService = TagService()

    @BeforeEach
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
        createDummyTag("t3", "tag3")
    }

    @Test
    fun testGetAllTags() {
        val tags = tagService.getAll(TEST_USER)
        assertThat(tags).hasSize(3)
        assertThat(tags).extracting("id").doesNotHaveDuplicates()
    }

    @Test
    fun testGetTagById() {
        val tag = tagService.get(TEST_USER, "t1")
        assertThat(tag?.id).isEqualTo("t1")
        assertThat(tag?.name).isEqualTo("tag1")
        assertThat(tag?.path).isEqualTo("tag1")

        val tag2 = tagService.get(TEST_USER, "t2")
        assertThat(tag2?.id).isEqualTo("t2")
        assertThat(tag2?.name).isEqualTo("tag2")
        assertThat(tag2?.path).isEqualTo("tag2")
    }

    @Test
    fun testGetTagDoesntExist() {
        assertThat(tagService.get(TEST_USER, "invalid")).isNull()
    }

    @Test
    fun testGetTagsByIds() {
        val tags = tagService.getIn(TEST_USER, listOf("t1", "t2"))
        assertThat(tags).hasSize(2).extracting("id").containsExactlyInAnyOrder("t1", "t2")

        val tags2 = tagService.getIn(TEST_USER, listOf("t3", "invalid"))
        assertThat(tags2).hasSize(1).extracting("id").containsExactlyInAnyOrder("t3")
    }

    @Test
    fun testDeleteTagDoesntExist() {
        assertThat(tagService.delete(TEST_USER, "invalid")).isFalse()
    }

    @Test
    fun testDeleteTagNoChildren() {
        assertThat(tagService.delete(TEST_USER, "t1")).isTrue()
        assertThat(tagService.getAll(TEST_USER)).hasSize(2).extracting("id").containsExactly("t2", "t3")
        assertThat(tagService.get(TEST_USER, "t1")).isNull()
    }

    @Test
    fun testDeleteTagLinkedToEntry() {
        val noteService = NoteService(
            GroupSetService(tagService, CollectionService()),
            mockk(relaxUnitFun = true), mockk(), mockk(relaxUnitFun = true), MarkdownProcessor(mockk(), mockk())
        )
        val note = noteService.add(TEST_USER, NewNote(null, "n1", "content", listOf("t1")))
        assertThat(note.tags).hasSize(1).extracting("id").containsOnly("t1")
        assertThat(tagService.delete(TEST_USER, "t1")).isTrue()
        assertThat(noteService.get(TEST_USER, note.id)?.tags).isEmpty()
    }

    @Test
    fun testCreateTagNoParent() {
        val created = tagService.add(TEST_USER, NewTag(null, "newTag"))
        assertThat(created.name).isEqualTo("newTag")
        assertThat(created.path).isEqualTo("newTag")
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)

        assertThat(tagService.getAll(TEST_USER)).hasSize(4).extracting("id").contains(created.id)
        val retr = tagService.get(TEST_USER, created.id)
        assertThat(retr).isNotNull
        assertThat(retr).isEqualTo(created)
        assertThat(retr?.dateCreated).isEqualTo(retr?.dateUpdated)
    }

    @Test
    fun testUpdateTagNoId() {
        val res = tagService.update(TEST_USER, NewTag(null, "newTag"))
        assertThat(res?.name).isEqualTo("newTag")

        assertThat(tagService.getAll(TEST_USER)).hasSize(4)
        assertThat(tagService.get(TEST_USER, res!!.id)).isEqualTo(res)
    }

    @Test
    fun testUpdateTag() {
        val current = tagService.get(TEST_USER, "t1")
        assertThat(current).isNotNull
        Thread.sleep(10) // makes sure timestamps are different
        val updated = tagService.update(TEST_USER, NewTag("t1", "updated"))
        val retr = tagService.get(TEST_USER, "t1")
        assertThat(updated).isEqualTo(retr)
        assertThat(retr).isNotNull
        assertThat(retr?.name).isEqualTo("updated")
        assertThat(retr?.path).isEqualTo("updated")
        assertThat(retr?.dateUpdated).isNotEqualTo(current?.dateUpdated)
        assertThat(retr?.dateCreated).isEqualTo(current?.dateCreated)
        assertThat(retr?.dateCreated).isNotEqualTo(retr?.dateUpdated)
    }

    @Test
    fun testUpdateTagDoesntExist() {
        assertThat(tagService.update(TEST_USER, NewTag("invalid", "name"))).isNull()
    }

    @Test
    fun testGetAllAsSequence() {
        val all = tagService.sequence(TEST_USER).toList()
        assertThat(all).hasSize(3)
    }

}
