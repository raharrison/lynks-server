package service

import common.DatabaseTest
import common.NewNote
import entry.NoteService
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tag.NewTag
import tag.TagService
import util.createDummyTag

class TagServiceTest : DatabaseTest() {

    private val tagService = TagService()

    @BeforeEach
    fun createTags() {
        /*
         t1                t2
                            |
                            |
                      ------+------
                     t3           t4
                      |            |
                      |            |
                -----+------       +----
               t5         t6           t7
               |
               |
           ----+
          t8
         */

        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
        createDummyTag("t3", "tag3", "t2")
        createDummyTag("t4", "tag4", "t2")
        createDummyTag("t5", "tag5", "t3")
        createDummyTag("t6", "tag6", "t3")
        createDummyTag("t7", "tag7", "t4")
        createDummyTag("t8", "tag8", "t5")
    }

    @Test
    fun testGetAllTags() {
        val tags = tagService.getAllTags()
        assertThat(tags).hasSize(2)
        assertThat(tags).extracting("id").doesNotHaveDuplicates()
    }

    @Test
    fun testGetTagById() {
        val tag = tagService.getTag("t1")
        assertThat(tag?.id).isEqualTo("t1")
        assertThat(tag?.name).isEqualTo("tag1")
        assertThat(tag?.children).isEmpty()

        val tag2 = tagService.getTag("t2")
        assertThat(tag2?.id).isEqualTo("t2")
        assertThat(tag2?.name).isEqualTo("tag2")
        assertThat(tag2?.children).hasSize(2).extracting("id").containsExactlyInAnyOrder("t3", "t4")
    }

    @Test
    fun testGetTagDoesntExist() {
        assertThat(tagService.getTag("invalid")).isNull()
    }

    @Test
    fun testGetTagsByIds() {
        val tags = tagService.getTags(listOf("t1", "t2"))
        assertThat(tags).hasSize(2).extracting("id").containsExactlyInAnyOrder("t1", "t2")

        val tags2 = tagService.getTags(listOf("t3", "invalid"))
        assertThat(tags2).hasSize(1).extracting("id").containsExactlyInAnyOrder("t3")
    }

    @Test
    fun testGetSubtree() {
        val tree = tagService.subtree("t3")
        assertThat(tree).hasSize(4).extracting("id").containsExactlyInAnyOrder("t3", "t5", "t6", "t8")

        val tree2 = tagService.subtree("t1")
        assertThat(tree2).hasSize(1).extracting("id").containsExactlyInAnyOrder("t1")
    }

    @Test
    fun testGetSubtreeDoesntExist() {
        assertThat(tagService.subtree("invalid")).isEmpty()
    }

    @Test
    fun testDeleteTagDoesntExist() {
        assertThat(tagService.deleteTag("invalid")).isFalse()
    }

    @Test
    fun testDeleteTagNoChildren() {
        assertThat(tagService.deleteTag("t1")).isTrue()
        assertThat(tagService.getAllTags()).hasSize(1).extracting("id").containsExactly("t2")
        assertThat(tagService.getTag("t1")).isNull()
    }

    @Test
    fun testDeleteTagLinkedToEntry() {
        val noteService = NoteService(tagService, mockk())
        val note = noteService.add(NewNote(null, "n1", "content", listOf("t1")))
        assertThat(note.tags).hasSize(1).extracting("id").containsOnly("t1")
        assertThat(tagService.deleteTag("t1")).isTrue()
        assertThat(noteService.get(note.id)?.tags).isEmpty()
    }

    @Test
    fun testDeleteTagWithChildren() {
        assertThat(tagService.deleteTag("t5")).isTrue()
        assertThat(tagService.getTag("t5")).isNull()
        assertThat(tagService.getTag("t8")).isNull()
        assertThat(tagService.getTags(listOf("t5", "t8"))).isEmpty()

        assertThat(tagService.deleteTag("t2")).isTrue()
        assertThat(tagService.getAllTags()).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(tagService.getTag("t2")).isNull()
        assertThat(tagService.getTag("t3")).isNull()
        assertThat(tagService.getTag("t4")).isNull()
    }

    @Test
    fun testCreateTagNoParent() {
        val created = tagService.addTag(NewTag(null, "newTag", null))
        assertThat(created.name).isEqualTo("newTag")
        assertThat(created.children).isEmpty()

        assertThat(tagService.getAllTags()).hasSize(3).extracting("id").contains(created.id)
        val retr = tagService.getTag(created.id)
        assertThat(retr).isNotNull()
        assertThat(retr).isEqualTo(created)
    }

    @Test
    fun testCreateTagWithParent() {
        val created = tagService.addTag(NewTag(null, "newTag", "t1"))
        assertThat(created.name).isEqualTo("newTag")
        assertThat(created.children).isEmpty()

        assertThat(tagService.getAllTags()).hasSize(2)

        val parent = tagService.getTag("t1")
        assertThat(parent?.children).hasSize(1).extracting("id").containsExactly(created.id)

        val subtree = tagService.subtree("t1")
        assertThat(subtree).hasSize(2).extracting("id").containsExactlyInAnyOrder("t1", created.id)

        val retr = tagService.getTag(created.id)
        assertThat(retr).isNotNull()
        assertThat(retr?.children).isEmpty()
    }

    @Test
    fun testUpdateTagNoId() {
        val res = tagService.updateTag(NewTag(null, "newTag", null))
        assertThat(res.name).isEqualTo("newTag")

        assertThat(tagService.getAllTags()).hasSize(3)
        assertThat(tagService.getTag(res.id)).isEqualTo(res)
    }

    @Test
    fun testUpdateTag() {
        val current = tagService.getTag("t1")
        assertThat(current).isNotNull()
        val updated = tagService.updateTag(NewTag("t1", "updated", null))
        val retr = tagService.getTag("t1")
        assertThat(updated).isEqualTo(retr)
        assertThat(retr).isNotNull()
        assertThat(retr?.name).isEqualTo("updated")
        assertThat(retr?.dateUpdated).isNotEqualTo(current?.dateUpdated)
    }

    @Test
    fun testUpdateTagParent() {
        val tag = tagService.updateTag(NewTag("t1", "tag1", "t8"))
        val retr = tagService.getTag("t1")
        assertThat(retr).isEqualTo(tag)
        assertThat(retr?.children).isEmpty()
        assertThat(tagService.getAllTags()).hasSize(1)
        val parent = tagService.getTag("t8")
        assertThat(parent?.children).hasSize(1).extracting("id").containsExactly("t1")

        val noParent = tagService.updateTag(NewTag("t3", "tag3", null))
        val retr2 = tagService.getTag("t3")
        assertThat(retr2).isEqualTo(noParent)
        assertThat(retr2?.children).hasSize(2)
        assertThat(tagService.getAllTags()).hasSize(2).extracting("id").containsExactly("t2", "t3")

        assertThat(tagService.getTag("t2")?.children).hasSize(1).extracting("id").containsExactly("t4")
    }

}