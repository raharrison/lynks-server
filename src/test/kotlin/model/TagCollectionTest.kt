package model

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import tag.Tag
import tag.TagCollection

class TagCollectionTest {

    private lateinit var collection: TagCollection

    private fun createTag(id: String, vararg children: Tag): Tag =
            Tag("t$id", id, children.toMutableSet(), 123456)

    @Before
    fun setup() {
        collection = TagCollection()

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

        val tag8 = createTag("8")
        val tag7 = createTag("7")
        val tag6 = createTag("6")
        val tag5 = createTag("5", tag8)
        val tag4 = createTag("4", tag7)
        val tag3 = createTag("3", tag5, tag6)
        val tag2 = createTag("2", tag3, tag4)
        val tag1 = createTag("1")

        collection.build(listOf(tag1, tag2))
    }

    @Test
    fun testGetTag() {
        val lookup1 = collection.tag("t1")
        assertThat(lookup1).isNotNull()
        assertThat(lookup1?.id).isEqualTo("t1")
        assertThat(lookup1?.name).isEqualTo("1")
        assertThat(lookup1?.children).isEmpty()

        val lookup2 = collection.tag("t2")
        assertThat(lookup2).isNotNull()
        assertThat(lookup2?.id).isEqualTo("t2")
        assertThat(lookup2?.name).isEqualTo("2")
        assertThat(lookup2?.children).hasSize(2).extracting("id").containsExactlyInAnyOrder("t3", "t4")

        val lookup3 = collection.tag("t3")
        assertThat(lookup3).isNotNull()
        assertThat(lookup3?.id).isEqualTo("t3")
        assertThat(lookup3?.name).isEqualTo("3")
        assertThat(lookup3?.children).hasSize(2).extracting("id").containsExactlyInAnyOrder("t5", "t6")

        assertThat(collection.tag("none")).isNull()
    }

    @Test
    fun testAll() {
        assertThat(collection.all()).hasSize(8)
    }

    @Test
    fun testTagsIn() {
        val tags = collection.tagsIn(listOf("t1", "t2", "other"))
        assertThat(tags).hasSize(2)
        assertThat(tags).extracting("id").containsExactlyInAnyOrder("t1", "t2")
        assertThat(tags).extracting("name").containsExactlyInAnyOrder("1", "2")
    }

    @Test
    fun testAdd() {
        var add = createTag("9")
        var added = collection.add(add, null)
        assertThat(added).isEqualTo(add)
        assertThat(collection.all()).hasSize(9)
        assertThat(collection.tag("t9")).isEqualTo(add)

        add = createTag("10")
        added = collection.add(add, "t7")
        assertThat(added).isEqualTo(add)
        assertThat(collection.all()).hasSize(10)
        assertThat(collection.tag("t10")).isEqualTo(add)
        assertThat(collection.tag("t7")?.children).hasSize(1).containsExactly(add)
        assertThat(collection.subtree("t4")).hasSize(3).extracting("id").containsExactlyInAnyOrder("t4", "t7", "t10")
        assertThat(collection.subtree("t2")).hasSize(8).extracting("id").contains("t4", "t7", "t3", "t10")

        add = createTag("11")
        added = collection.add(add, "t3")
        assertThat(added).isEqualTo(add)
        assertThat(collection.all()).hasSize(11)
        assertThat(collection.tag("t11")).isEqualTo(add)
        assertThat(collection.tag("t3")?.children).hasSize(3).extracting("id").containsExactlyInAnyOrder("t5", "t6", "t11")
        assertThat(collection.subtree("t3")).hasSize(5).extracting("id").containsExactlyInAnyOrder("t3", "t5", "t8", "t6", "t11")
        assertThat(collection.subtree("t2")).hasSize(9).extracting("id").contains("t4", "t7", "t3", "t10", "t3", "t5", "t6", "t11")
    }

    @Test
    fun testUpdate() {
        var update = collection.tag("t5")!!
        collection.update("t4", update)

        var updated = collection.tag("t5")
        assertThat(updated?.children).hasSize(1)
        assertThat(collection.all()).hasSize(8)
        assertThat(collection.tag("t3")?.children).hasSize(1)
        assertThat(collection.tag("t5")?.children).hasSize(1)
        assertThat(collection.tag("t4")?.children).hasSize(2).extracting("id").contains("t5")
        assertThat(collection.subtree("t5")).hasSize(2).extracting("id").containsExactlyInAnyOrder("t5", "t8")
        assertThat(collection.subtree("t3")).hasSize(2).extracting("id").containsExactlyInAnyOrder("t3", "t6")
        assertThat(collection.subtree("t4")).hasSize(4).extracting("id").containsExactlyInAnyOrder("t4", "t7", "t5", "t8")
        assertThat(collection.subtree("t2")).hasSize(7)

        update = collection.tag("t3")!!
        collection.update("t7", update)

        updated = collection.tag("t3")
        assertThat(updated?.children).hasSize(1)
        assertThat(collection.all()).hasSize(8)
        assertThat(collection.tag("t2")?.children).hasSize(1)
        assertThat(collection.tag("t3")?.children).hasSize(1)
        assertThat(collection.tag("t7")?.children).hasSize(1).extracting("id").containsExactly("t3")
        assertThat(collection.subtree("t5")).hasSize(2).extracting("id").containsExactlyInAnyOrder("t5", "t8")
        assertThat(collection.subtree("t3")).hasSize(2).extracting("id").containsExactlyInAnyOrder("t3", "t6")
        assertThat(collection.subtree("t4")).hasSize(6).extracting("id").containsExactlyInAnyOrder("t4", "t7", "t5", "t8", "t3", "t6")
        assertThat(collection.subtree("t7")).hasSize(3).extracting("id").containsExactlyInAnyOrder("t7", "t3", "t6")
        assertThat(collection.subtree("t2")).hasSize(7)
    }

    @Test
    fun testUpdateLeafParent() {
        var update = collection.tag("t7")!!
        collection.update("t1", update)

        var updated = collection.tag("t7")
        assertThat(updated?.children).isEmpty()
        assertThat(collection.all()).hasSize(8)
        assertThat(collection.tag("t4")?.children).isEmpty()
        assertThat(collection.subtree("t1")).hasSize(2).extracting("id").containsExactlyInAnyOrder("t1", "t7")
        assertThat(collection.subtree("t4")).hasSize(1).extracting("id").containsExactlyInAnyOrder("t4")
        assertThat(collection.subtree("t2")).hasSize(6).extracting("id").doesNotContain("t7")

        update = collection.tag("t6")!!
        collection.update("t1", update)

        updated = collection.tag("t6")
        assertThat(updated?.children).isEmpty()
        assertThat(collection.all()).hasSize(8)
        assertThat(collection.tag("t3")?.children).hasSize(1)
        assertThat(collection.subtree("t1")).hasSize(3).extracting("id").containsExactlyInAnyOrder("t1", "t7", "t6")
        assertThat(collection.subtree("t3")).hasSize(3).extracting("id").containsExactlyInAnyOrder("t3", "t5", "t8")
        assertThat(collection.subtree("t2")).hasSize(5).extracting("id").doesNotContain("t6")
    }

    @Test
    fun testUpdateName() {
        var tag = collection.tag("t1")!!
        assertThat(tag.name).isEqualTo("1")
        tag.name = "edited"
        collection.update(null, tag)

        tag = collection.tag("t1")!!
        assertThat(tag.name).isEqualTo("edited")
        assertThat(tag.children).hasSize(0)
        assertThat(collection.subtree("t1")).hasSize(1)
    }

    @Test
    fun testDelete() {
        // delete child node with no children
        collection.delete("t6")
        assertThat(collection.all()).hasSize(7).extracting("id").doesNotContain("t6")
        assertThat(collection.tag("t6")).isNull()
        assertThat(collection.tag("t3")?.children).hasSize(1).extracting("id").doesNotContain("t6")
        assertThat(collection.subtree("t3")).hasSize(3).extracting("id").doesNotContain("t6")
        assertThat(collection.subtree("t2")).hasSize(6)

        // delete child node with children
        collection.delete("t4")
        assertThat(collection.all()).hasSize(5).extracting("id").doesNotContain("t4", "t7")
        assertThat(collection.tag("t4")).isNull()
        assertThat(collection.tag("t2")?.children).hasSize(1).extracting("id").doesNotContain("t4")
        assertThat(collection.subtree("t2")).hasSize(4).extracting("id").doesNotContain("t4", "t7")

        // delete root node with children
        collection.delete("t2")
        assertThat(collection.all()).hasSize(1)
        assertThat(collection.all()).extracting("id").doesNotContain("t2", "t3", "t5", "t8")
        assertThat(collection.tag("t2")).isNull()
        assertThat(collection.tag("t3")).isNull()
        assertThat(collection.tag("t8")).isNull()
        assertThat(collection.subtree("t2")).isEmpty()
        assertThat(collection.subtree("t3")).isEmpty()
        assertThat(collection.subtree("t8")).isEmpty()
        assertThat(collection.subtree("t1")).hasSize(1)

        // delete root node with no children
        collection.delete("t1")
        assertThat(collection.all()).isEmpty()
        assertThat(collection.tag("t1")).isNull()
    }

    @Test
    fun testSubTree() {
        val empty = collection.subtree("t1")
        assertThat(empty).extracting("id").containsExactly("t1")

        var tree = collection.subtree("t2")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("t2", "t3", "t4", "t5", "t6", "t7", "t8")

        tree = collection.subtree("t3")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("t3", "t5", "t6", "t8")

        tree = collection.subtree("t4")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("t4", "t7")

        tree = collection.subtree("t5")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("t5", "t8")

        tree = collection.subtree("t6")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("t6")

        tree = collection.subtree("t7")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("t7")

        tree = collection.subtree("t8")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("t8")

        assertThat(collection.subtree("none")).isEmpty()
    }

}