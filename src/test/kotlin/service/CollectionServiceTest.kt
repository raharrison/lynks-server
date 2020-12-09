package service

import common.DatabaseTest
import common.NewNote
import entry.NoteService
import group.CollectionService
import group.GroupSetService
import group.NewCollection
import group.TagService
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import util.createDummyCollection

class CollectionServiceTest : DatabaseTest() {

    private val collectionService = CollectionService()

    @BeforeEach
    fun createCollections() {
        /*
         c1                c2
                            |
                            |
                      ------+------
                     c3           c4
                      |            |
                      |            |
                -----+------       +----
               c5         c6           c7
               |
               |
           ----+
          c8
         */

        createDummyCollection("c1", "col1")
        createDummyCollection("c2", "col2")
        createDummyCollection("c3", "col3", "c2")
        createDummyCollection("c4", "col4", "c2")
        createDummyCollection("c5", "col5", "c3")
        createDummyCollection("c6", "col6", "c3")
        createDummyCollection("c7", "col7", "c4")
        createDummyCollection("c8", "col8", "c5")
    }

    @Test
    fun testGetAllCollections() {
        val collections = collectionService.getAll()
        assertThat(collections).hasSize(2)
        assertThat(collections).extracting("id").doesNotHaveDuplicates()
    }

    @Test
    fun testGetCollectionById() {
        val collection = collectionService.get("c1")
        assertThat(collection?.id).isEqualTo("c1")
        assertThat(collection?.name).isEqualTo("col1")
        assertThat(collection?.path).isEqualTo("col1")
        assertThat(collection?.children).isEmpty()

        val col2 = collectionService.get("c2")
        assertThat(col2?.id).isEqualTo("c2")
        assertThat(col2?.name).isEqualTo("col2")
        assertThat(col2?.path).isEqualTo("col2")
        assertThat(col2?.children).hasSize(2).extracting("id").containsExactlyInAnyOrder("c3", "c4")
    }

    @Test
    fun testGetCollectionDoesntExist() {
        assertThat(collectionService.get("invalid")).isNull()
    }

    @Test
    fun testGetCollectionsByIds() {
        val collections = collectionService.getIn(listOf("c1", "c2"))
        assertThat(collections).hasSize(2).extracting("id").containsExactlyInAnyOrder("c1", "c2")

        val collections2 = collectionService.getIn(listOf("c3", "invalid"))
        assertThat(collections2).hasSize(1).extracting("id").containsExactlyInAnyOrder("c3")
    }

    @Test
    fun testGetSubtree() {
        val tree = collectionService.subtree("c3")
        assertThat(tree).hasSize(4).extracting("id").containsExactlyInAnyOrder("c3", "c5", "c6", "c8")

        val tree2 = collectionService.subtree("c1")
        assertThat(tree2).hasSize(1).extracting("id").containsExactlyInAnyOrder("c1")
    }

    @Test
    fun testGetSubtreeDoesntExist() {
        assertThat(collectionService.subtree("invalid")).isEmpty()
    }

    @Test
    fun testDeleteCollectionDoesntExist() {
        assertThat(collectionService.delete("invalid")).isFalse()
    }

    @Test
    fun testDeleteCollectionNoChildren() {
        assertThat(collectionService.delete("c1")).isTrue()
        assertThat(collectionService.getAll()).hasSize(1).extracting("id").containsExactly("c2")
        assertThat(collectionService.get("c1")).isNull()
    }

    @Test
    fun testDeleteCollectionLinkedToEntry() {
        val noteService = NoteService(GroupSetService(TagService(), collectionService), mockk(relaxUnitFun = true), mockk())
        val note = noteService.add(NewNote(null, "n1", "content", emptyList(), listOf("c1")))
        assertThat(note.collections).hasSize(1).extracting("id").containsOnly("c1")
        assertThat(collectionService.delete("c1")).isTrue()
        assertThat(noteService.get(note.id)?.collections).isEmpty()
    }

    @Test
    fun testDeleteCollectionWithChildren() {
        assertThat(collectionService.delete("c5")).isTrue()
        assertThat(collectionService.get("c5")).isNull()
        assertThat(collectionService.get("c8")).isNull()
        assertThat(collectionService.getIn(listOf("c5", "c8"))).isEmpty()

        assertThat(collectionService.delete("c2")).isTrue()
        assertThat(collectionService.getAll()).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(collectionService.get("c2")).isNull()
        assertThat(collectionService.get("c3")).isNull()
        assertThat(collectionService.get("c4")).isNull()
    }

    @Test
    fun testCreateCollectionNoParent() {
        val created = collectionService.add(NewCollection(null, "newCollection", null))
        assertThat(created.name).isEqualTo("newCollection")
        assertThat(created.path).isEqualTo("newCollection")
        assertThat(created.children).isEmpty()

        assertThat(collectionService.getAll()).hasSize(3).extracting("id").contains(created.id)
        val retr = collectionService.get(created.id)
        assertThat(retr).isNotNull
        assertThat(retr).isEqualTo(created)
        assertThat(retr?.dateCreated).isEqualTo(retr?.dateUpdated)
    }

    @Test
    fun testCreateCollectionWithParent() {
        val created = collectionService.add(NewCollection(null, "newCollection", "c1"))
        assertThat(created.name).isEqualTo("newCollection")
        assertThat(created.path).isEqualTo("col1/newCollection")
        assertThat(created.children).isEmpty()

        assertThat(collectionService.getAll()).hasSize(2)

        val parent = collectionService.get("c1")
        assertThat(parent?.children).hasSize(1).extracting("id").containsExactly(created.id)

        val subtree = collectionService.subtree("c1")
        assertThat(subtree).hasSize(2).extracting("id").containsExactlyInAnyOrder("c1", created.id)

        val retr = collectionService.get(created.id)
        assertThat(retr).isNotNull
        assertThat(retr?.children).isEmpty()
        assertThat(retr?.dateCreated).isEqualTo(retr?.dateUpdated)
    }

    @Test
    fun testUpdateCollectionNoId() {
        val res = collectionService.update(NewCollection(null, "newCollection"))
        assertThat(res?.name).isEqualTo("newCollection")

        assertThat(collectionService.getAll()).hasSize(3)
        assertThat(collectionService.get(res!!.id)).isEqualTo(res)
    }

    @Test
    fun testUpdateCollection() {
        val current = collectionService.get("c1")
        assertThat(current).isNotNull
        Thread.sleep(10) // makes sure timestamps are different
        val updated = collectionService.update(NewCollection("c1", "updated"))
        val retr = collectionService.get("c1")
        assertThat(updated).isEqualTo(retr)
        assertThat(retr).isNotNull
        assertThat(retr?.name).isEqualTo("updated")
        assertThat(retr?.path).isEqualTo("updated")
        assertThat(retr?.dateUpdated).isNotEqualTo(current?.dateUpdated)
        assertThat(retr?.dateCreated).isEqualTo(current?.dateCreated)
        assertThat(retr?.dateCreated).isNotEqualTo(retr?.dateUpdated)
    }

    @Test
    fun testUpdateCollectionParent() {
        val col = collectionService.update(NewCollection("c1", "col1", "c8"))
        val retr = collectionService.get("c1")
        assertThat(retr).isEqualTo(col)
        assertThat(retr?.children).isEmpty()
        assertThat(retr?.path).isEqualTo("col2/col3/col5/col8/col1")
        assertThat(collectionService.getAll()).hasSize(1)
        val parent = collectionService.get("c8")
        assertThat(parent?.children).hasSize(1).extracting("id").containsExactly("c1")

        val noParent = collectionService.update(NewCollection("c3", "col3"))
        val retr2 = collectionService.get("c3")
        assertThat(retr2).isEqualTo(noParent)
        assertThat(retr2?.path).isEqualTo("col3")
        assertThat(retr2?.children).hasSize(2)
        assertThat(collectionService.getAll()).hasSize(2).extracting("id").containsExactly("c2", "c3")

        assertThat(collectionService.get("c2")?.children).hasSize(1).extracting("id").containsExactly("c4")
    }

    @Test
    fun testUpdateCollectionDoesntExist() {
        assertThat(collectionService.update(NewCollection("invalid", "name"))).isNull()
    }

    @Test
    fun testGetAllAsSequence() {
        val all = collectionService.sequence().toList()
        assertThat(all).hasSize(8)
    }

}
