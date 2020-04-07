package service

import common.DatabaseTest
import common.NewNote
import entry.NoteService
import group.CollectionService
import group.GroupSetService
import group.NewTag
import group.TagService
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import util.createDummyTag

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
        val tags = tagService.getAll()
        assertThat(tags).hasSize(3)
        assertThat(tags).extracting("id").doesNotHaveDuplicates()
    }

    @Test
    fun testGetTagById() {
        val tag = tagService.get("t1")
        assertThat(tag?.id).isEqualTo("t1")
        assertThat(tag?.name).isEqualTo("tag1")

        val tag2 = tagService.get("t2")
        assertThat(tag2?.id).isEqualTo("t2")
        assertThat(tag2?.name).isEqualTo("tag2")
    }

    @Test
    fun testGetTagDoesntExist() {
        assertThat(tagService.get("invalid")).isNull()
    }

    @Test
    fun testGetTagsByIds() {
        val tags = tagService.getIn(listOf("t1", "t2"))
        assertThat(tags).hasSize(2).extracting("id").containsExactlyInAnyOrder("t1", "t2")

        val tags2 = tagService.getIn(listOf("t3", "invalid"))
        assertThat(tags2).hasSize(1).extracting("id").containsExactlyInAnyOrder("t3")
    }

    @Test
    fun testDeleteTagDoesntExist() {
        assertThat(tagService.delete("invalid")).isFalse()
    }

    @Test
    fun testDeleteTagNoChildren() {
        assertThat(tagService.delete("t1")).isTrue()
        assertThat(tagService.getAll()).hasSize(2).extracting("id").containsExactly("t2", "t3")
        assertThat(tagService.get("t1")).isNull()
    }

    @Test
    fun testDeleteTagLinkedToEntry() {
        val noteService = NoteService(GroupSetService(tagService, CollectionService()), mockk(relaxUnitFun = true), mockk())
        val note = noteService.add(NewNote(null, "n1", "content", listOf("t1")))
        assertThat(note.tags).hasSize(1).extracting("id").containsOnly("t1")
        assertThat(tagService.delete("t1")).isTrue()
        assertThat(noteService.get(note.id)?.tags).isEmpty()
    }

    @Test
    fun testCreateTagNoParent() {
        val created = tagService.add(NewTag(null, "newTag"))
        assertThat(created.name).isEqualTo("newTag")
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)

        assertThat(tagService.getAll()).hasSize(4).extracting("id").contains(created.id)
        val retr = tagService.get(created.id)
        assertThat(retr).isNotNull
        assertThat(retr).isEqualTo(created)
        assertThat(retr?.dateCreated).isEqualTo(retr?.dateUpdated)
    }

    @Test
    fun testUpdateTagNoId() {
        val res = tagService.update(NewTag(null, "newTag"))
        assertThat(res?.name).isEqualTo("newTag")

        assertThat(tagService.getAll()).hasSize(4)
        assertThat(tagService.get(res!!.id)).isEqualTo(res)
    }

    @Test
    fun testUpdateTag() {
        val current = tagService.get("t1")
        assertThat(current).isNotNull
        Thread.sleep(10) // makes sure timestamps are different
        val updated = tagService.update(NewTag("t1", "updated"))
        val retr = tagService.get("t1")
        assertThat(updated).isEqualTo(retr)
        assertThat(retr).isNotNull
        assertThat(retr?.name).isEqualTo("updated")
        assertThat(retr?.dateUpdated).isNotEqualTo(current?.dateUpdated)
        assertThat(retr?.dateCreated).isEqualTo(current?.dateCreated)
        assertThat(retr?.dateCreated).isNotEqualTo(retr?.dateUpdated)
    }

    @Test
    fun testUpdateTagDoesntExist() {
        assertThat(tagService.update(NewTag("invalid", "name"))).isNull()
    }

}