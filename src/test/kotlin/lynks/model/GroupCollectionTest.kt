package lynks.model

import lynks.group.Collection
import lynks.group.GroupCollection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GroupCollectionTest {

    private lateinit var collection: GroupCollection<Collection>

    private fun createCollection(id: String, vararg children: Collection): Collection =
            Collection("g$id", id, null, children.toMutableSet(), 123456, 67891)

    @BeforeEach
    fun setup() {
        collection = GroupCollection()

        /*
         g1                g2
                            |
                            |
                      ------+------
                     g3           g4
                      |            |
                      |            |
                -----+------       +----
               g5         g6           g7
               |
               |
           ----+
          g8
         */

        val group8 = createCollection("8")
        val group7 = createCollection("7")
        val group6 = createCollection("6")
        val group5 = createCollection("5", group8)
        val group4 = createCollection("4", group7)
        val group3 = createCollection("3", group5, group6)
        val group2 = createCollection("2", group3, group4)
        val group1 = createCollection("1")

        collection.build(listOf(group1,group2))
    }

    @Test
    fun testGetGroup() {
        val lookup1 = collection.group("g1")
        assertThat(lookup1).isNotNull
        assertThat(lookup1?.id).isEqualTo("g1")
        assertThat(lookup1?.name).isEqualTo("1")
        assertThat(lookup1?.path).isEqualTo("1")
        assertThat(lookup1?.children).isEmpty()

        val lookup2 = collection.group("g2")
        assertThat(lookup2).isNotNull
        assertThat(lookup2?.id).isEqualTo("g2")
        assertThat(lookup2?.name).isEqualTo("2")
        assertThat(lookup2?.path).isEqualTo("2")
        assertThat(lookup2?.children).hasSize(2).extracting("id").containsExactlyInAnyOrder("g3", "g4")

        val lookup3 = collection.group("g3")
        assertThat(lookup3).isNotNull
        assertThat(lookup3?.id).isEqualTo("g3")
        assertThat(lookup3?.name).isEqualTo("3")
        assertThat(lookup3?.path).isEqualTo("2/3")
        assertThat(lookup3?.children).hasSize(2).extracting("id").containsExactlyInAnyOrder("g5", "g6")

        assertThat(collection.group("none")).isNull()
    }

    @Test
    fun testAll() {
        assertThat(collection.all()).hasSize(8)
    }

    @Test
    fun testGroupsIn() {
        val groups = collection.groupsIn(listOf("g1", "g2", "other"))
        assertThat(groups).hasSize(2)
        assertThat(groups).extracting("id").containsExactlyInAnyOrder("g1", "g2")
        assertThat(groups).extracting("name").containsExactlyInAnyOrder("1", "2")
        assertThat(groups).extracting("path").containsExactlyInAnyOrder("1", "2")
    }

    @Test
    fun testGroupByPath() {
        val g1 = collection.groupByPath("1")
        assertThat(g1?.id).isEqualTo("g1")
        val g6 = collection.groupByPath("2/3/6")
        assertThat(g6?.id).isEqualTo("g6")
        val notFound = collection.groupByPath("2/3/invalid")
        assertThat(notFound).isNull()
    }

    @Test
    fun testAdd() {
        var add = createCollection("9")
        var added = collection.add(add, null)
        assertThat(added).isEqualTo(add)
        assertThat(collection.all()).hasSize(9)
        assertThat(collection.group("g9")).isEqualTo(add).extracting("path").isEqualTo("9")

        add = createCollection("10")
        added = collection.add(add, "g7")
        assertThat(added).isEqualTo(add)
        assertThat(collection.all()).hasSize(10)
        assertThat(collection.group("g10")).isEqualTo(add).extracting("path").isEqualTo("2/4/7/10")
        assertThat(collection.group("g7")?.children).containsExactly(add).hasSize(1)
        assertThat(collection.subtree("g4")).hasSize(3).extracting("id").containsExactlyInAnyOrder("g4", "g7", "g10")
        assertThat(collection.subtree("g2")).hasSize(8).extracting("id").contains("g4", "g7", "g3", "g10")

        add = createCollection("11")
        added = collection.add(add, "g3")
        assertThat(added).isEqualTo(add)
        assertThat(collection.all()).hasSize(11)
        assertThat(collection.group("g11")).isEqualTo(add).extracting("path").isEqualTo("2/3/11")
        assertThat(collection.group("g3")?.children).hasSize(3).extracting("id")
            .containsExactlyInAnyOrder("g5", "g6", "g11")
        assertThat(collection.subtree("g3")).hasSize(5).extracting("id").containsExactlyInAnyOrder("g3", "g5", "g8", "g6", "g11")
        assertThat(collection.subtree("g2")).hasSize(9).extracting("id").contains("g4", "g7", "g3", "g10", "g3", "g5", "g6", "g11")
    }

    @Test
    fun testUpdate() {
        var update = collection.group("g5")!!
        assertThat(update.path).isEqualTo("2/3/5")
        collection.update(update, "g4")

        var updated = collection.group("g5")
        assertThat(updated?.children).hasSize(1)
        assertThat(updated?.path).isEqualTo("2/4/5")
        assertThat(collection.all()).hasSize(8)
        assertThat(collection.group("g3")?.children).hasSize(1)
        assertThat(collection.group("g5")?.children).hasSize(1)
        assertThat(collection.group("g4")?.children).hasSize(2).extracting("id").contains("g5")
        assertThat(collection.subtree("g5")).hasSize(2).extracting("id").containsExactlyInAnyOrder("g5", "g8")
        assertThat(collection.subtree("g3")).hasSize(2).extracting("id").containsExactlyInAnyOrder("g3", "g6")
        assertThat(collection.subtree("g4")).hasSize(4).extracting("id").containsExactlyInAnyOrder("g4", "g7", "g5", "g8")
        assertThat(collection.subtree("g2")).hasSize(7)

        update = collection.group("g3")!!
        collection.update(update, "g7")

        updated = collection.group("g3")
        assertThat(updated?.children).hasSize(1)
        assertThat(updated?.path).isEqualTo("2/4/7/3")
        assertThat(collection.all()).hasSize(8)
        assertThat(collection.group("g2")?.children).hasSize(1)
        assertThat(collection.group("g3")?.children).hasSize(1)
        assertThat(collection.group("g7")?.children).hasSize(1).extracting("id").containsExactly("g3")
        assertThat(collection.subtree("g5")).hasSize(2).extracting("id").containsExactlyInAnyOrder("g5", "g8")
        assertThat(collection.subtree("g3")).hasSize(2).extracting("id").containsExactlyInAnyOrder("g3", "g6")
        assertThat(collection.subtree("g4")).hasSize(6).extracting("id").containsExactlyInAnyOrder("g4", "g7", "g5", "g8", "g3", "g6")
        assertThat(collection.subtree("g7")).hasSize(3).extracting("id").containsExactlyInAnyOrder("g7", "g3", "g6")
        assertThat(collection.subtree("g2")).hasSize(7)
    }

    @Test
    fun testUpdateLeafParent() {
        var update = collection.group("g7")!!
        assertThat(update.path).isEqualTo("2/4/7")
        collection.update(update, "g1")

        var updated = collection.group("g7")
        assertThat(updated?.children).isEmpty()
        assertThat(updated?.path).isEqualTo("1/7")
        assertThat(collection.all()).hasSize(8)
        assertThat(collection.group("g4")?.children).isEmpty()
        assertThat(collection.subtree("g1")).hasSize(2).extracting("id").containsExactlyInAnyOrder("g1", "g7")
        assertThat(collection.subtree("g4")).hasSize(1).extracting("id").containsExactlyInAnyOrder("g4")
        assertThat(collection.subtree("g2")).hasSize(6).extracting("id").doesNotContain("g7")

        update = collection.group("g6")!!
        assertThat(update.path).isEqualTo("2/3/6")
        collection.update(update, "g1")

        updated = collection.group("g6")
        assertThat(updated?.children).isEmpty()
        assertThat(updated?.path).isEqualTo("1/6")
        assertThat(collection.all()).hasSize(8)
        assertThat(collection.group("g3")?.children).hasSize(1)
        assertThat(collection.subtree("g1")).hasSize(3).extracting("id").containsExactlyInAnyOrder("g1", "g7", "g6")
        assertThat(collection.subtree("g3")).hasSize(3).extracting("id").containsExactlyInAnyOrder("g3", "g5", "g8")
        assertThat(collection.subtree("g2")).hasSize(5).extracting("id").doesNotContain("g6")
    }

    @Test
    fun testUpdateName() {
        var group = collection.group("g1")!!
        assertThat(group.name).isEqualTo("1")
        assertThat(group.path).isEqualTo("1")
        group.name = "edited"
        collection.update(group, null)

        group = collection.group("g1")!!
        assertThat(group.name).isEqualTo("edited")
        assertThat(group.path).isEqualTo("edited")
        assertThat(group.children).hasSize(0)
        assertThat(collection.subtree("g1")).hasSize(1)
    }

    @Test
    fun testDelete() {
        // delete child node with no children
        collection.delete("g6")
        assertThat(collection.all()).hasSize(7).extracting("id").doesNotContain("g6")
        assertThat(collection.group("g6")).isNull()
        assertThat(collection.group("g3")?.children).hasSize(1).extracting("id").doesNotContain("g6")
        assertThat(collection.subtree("g3")).hasSize(3).extracting("id").doesNotContain("g6")
        assertThat(collection.subtree("g2")).hasSize(6)

        // delete child node with children
        collection.delete("g4")
        assertThat(collection.all()).hasSize(5).extracting("id").doesNotContain("g4", "g7")
        assertThat(collection.group("g4")).isNull()
        assertThat(collection.group("g2")?.children).hasSize(1).extracting("id").doesNotContain("g4")
        assertThat(collection.subtree("g2")).hasSize(4).extracting("id").doesNotContain("g4", "g7")

        // delete root node with children
        collection.delete("g2")
        assertThat(collection.all()).hasSize(1)
        assertThat(collection.all()).extracting("id").doesNotContain("g2", "g3", "g5", "g8")
        assertThat(collection.group("g2")).isNull()
        assertThat(collection.group("g3")).isNull()
        assertThat(collection.group("g8")).isNull()
        assertThat(collection.subtree("g2")).isEmpty()
        assertThat(collection.subtree("g3")).isEmpty()
        assertThat(collection.subtree("g8")).isEmpty()
        assertThat(collection.subtree("g1")).hasSize(1)

        // delete root node with no children
        collection.delete("g1")
        assertThat(collection.all()).isEmpty()
        assertThat(collection.group("g1")).isNull()
    }

    @Test
    fun testSubTree() {
        val empty = collection.subtree("g1")
        assertThat(empty).extracting("id").containsExactly("g1")

        var tree = collection.subtree("g2")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("g2", "g3", "g4", "g5", "g6", "g7", "g8")

        tree = collection.subtree("g3")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("g3", "g5", "g6", "g8")

        tree = collection.subtree("g4")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("g4", "g7")

        tree = collection.subtree("g5")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("g5", "g8")

        tree = collection.subtree("g6")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("g6")

        tree = collection.subtree("g7")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("g7")

        tree = collection.subtree("g8")
        assertThat(tree).extracting("id").containsExactlyInAnyOrder("g8")

        assertThat(collection.subtree("none")).isEmpty()
    }

    @Test
    fun testGetRootGroups() {
        var root = collection.rootGroups()
        assertThat(root).hasSize(2).extracting("id").containsExactly("g1", "g2")

        collection.delete("g1")
        root = collection.rootGroups()
        assertThat(root).hasSize(1).extracting("id").containsExactly("g2")
    }

}
