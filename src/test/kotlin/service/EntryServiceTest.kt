package service

import common.*
import entry.EntryService
import group.CollectionService
import group.TagService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import util.createDummyEntry
import util.updateDummyEntry

class EntryServiceTest: DatabaseTest() {

    private val tagService = TagService()
    private val collectionService = CollectionService()
    private val entryService = EntryService(tagService, collectionService)

    @BeforeEach
    fun createEntries() {
        createDummyEntry("id1", "link1", "link content", EntryType.LINK)
        Thread.sleep(10)// prevent having same creation timestamp
        createDummyEntry("id2", "note1", "note content", EntryType.NOTE)
        Thread.sleep(10)
        createDummyEntry("id3", "note2", "note content2", EntryType.NOTE)
    }

    @Test
    fun testGetByIdNoExists() {
        assertThat(entryService.get("nothing")).isNull()
    }

    @Test
    fun testGetById() {
        val retrieved1 = entryService.get("id1") as Link
        assertThat(retrieved1.id).isEqualTo("id1")
        assertThat(retrieved1.title).isEqualTo("link1")
        assertThat(retrieved1.url).isEqualTo("link content")
        assertThat(retrieved1.type).isEqualTo(EntryType.LINK)

        val retrieved2 = entryService.get("id2") as Note
        assertThat(retrieved2.id).isEqualTo("id2")
        assertThat(retrieved2.title).isEqualTo("note1")
        assertThat(retrieved2.plainText).isEqualTo("note content")
        assertThat(retrieved2.type).isEqualTo(EntryType.NOTE)
    }

    @Test
    fun testGetAll() {
        val retrieved = entryService.get()
        assertThat(retrieved).hasSize(3)
        assertThat(retrieved).extracting("id").containsExactlyInAnyOrder("id1", "id2", "id3")
        assertThat(retrieved).hasAtLeastOneElementOfType(Note::class.java)
        assertThat(retrieved).hasAtLeastOneElementOfType(Link::class.java)
    }

    @Test
    fun testGetByIds() {
        val retrieved = entryService.get(listOf("id2", "id3"))
        assertThat(retrieved).hasSize(2)
        assertThat(retrieved).extracting("id").containsExactlyInAnyOrder("id2", "id3")
    }

    @Test
    fun testGetByIdsAndPaged() {
        val retrieved = entryService.get(listOf("id1", "id2", "id3"), PageRequest(1, 1))
        assertThat(retrieved).hasSize(1)
        assertThat(retrieved).extracting("id").containsExactlyInAnyOrder("id2")
    }

    @Test
    fun testPaging() {
        // order by date updated
        val retrieved = entryService.get(PageRequest(2))
        assertThat(retrieved).hasSize(1)
        assertThat(retrieved).extracting("id").containsExactly("id1")
        assertThat(retrieved).hasOnlyElementsOfType(Link::class.java)

        val retrieved2 = entryService.get(PageRequest(0, 1))
        assertThat(retrieved2).hasSize(1)
        assertThat(retrieved2).extracting("id").containsExactly("id3")
        assertThat(retrieved2).hasOnlyElementsOfType(Note::class.java)
    }

    @Test
    fun testSearchSortOrdering() {
        val retrieved = entryService.get(PageRequest(0, 10, sort = "dateCreated", direction = SortDirection.ASC))
        assertThat(retrieved).extracting("id").containsExactly("id1", "id2", "id3")

        val retrieved2 = entryService.get(PageRequest(0, 10, sort = "dateCreated", direction = SortDirection.DESC))
        assertThat(retrieved2).extracting("id").containsExactly("id3", "id2", "id1")
    }

    @Test
    fun testSearchTitle() {
        val entries = entryService.search("note")
        assertThat(entries).hasSize(2)
        assertThat(entries).extracting("id").containsExactlyInAnyOrder("id2", "id3")
        assertThat(entries).hasOnlyElementsOfType(Note::class.java)

        val entries2 = entryService.search("link")
        assertThat(entries2).hasSize(1)
        assertThat(entries2).extracting("id").containsExactly("id1")
        assertThat(entries2).hasOnlyElementsOfType(Link::class.java)
    }

    @Test
    fun testSearchMultipleResults() {
        val entries = entryService.search("content")
        assertThat(entries).hasSize(2)
        assertThat(entries).extracting("id").containsExactlyInAnyOrder("id1", "id2")
        assertThat(entries).hasAtLeastOneElementOfType(Note::class.java)
        assertThat(entries).hasAtLeastOneElementOfType(Link::class.java)
    }

    @Test
    fun testSearchNoResults() {
        val entries = entryService.search("nothing")
        assertThat(entries).isEmpty()
    }

    @Test
    fun testCannotAdd() {
        assertThrows<NotImplementedError> {
            entryService.add(NewLink("id", "title", "url", emptyList()))
        }
    }

    @Test
    fun testCannotUpdate() {
        assertThrows<NotImplementedError> {
            entryService.update(NewLink("id", "title", "url", emptyList()))
        }
        assertThrows<NotImplementedError> {
            entryService.update(Link("id", "title", "url", "src", "content", 1L, 1L))
        }
    }

    @Test
    fun testStar() {
        val original = entryService.get("id1")
        assertThat(original?.starred).isFalse()
        val dateUpdated = original?.dateUpdated

        val star = entryService.star("id1", true)
        assertThat(star?.starred).isTrue()
        assertThat(star?.version).isOne()
        // date updated is same
        assertThat(star?.dateUpdated).isEqualTo(dateUpdated)

        val unstar = entryService.star("id1", false)
        assertThat(unstar?.starred).isFalse()
        assertThat(unstar?.version).isOne()
        assertThat(unstar?.dateUpdated).isEqualTo(dateUpdated)

        assertThat(entryService.get("id1")?.starred).isFalse()
    }

    @Test
    fun testSetStarInvalidEntry() {
        assertThat(entryService.star("invalid", true)).isNull()
        assertThat(entryService.star("invalid", false)).isNull()
    }

    @Test
    fun testGetHistory() {
        val history1 = entryService.getEntryVersions("id1")
        assertThat(history1).hasSize(1)
        assertThat(history1).extracting("id").containsOnly("id1")
        assertThat(history1).extracting("version").containsOnly(1)

        updateDummyEntry("id1", "updated", 2)

        val history2 = entryService.getEntryVersions("id1")
        assertThat(history2).hasSize(2)
        assertThat(history2).extracting("id").containsOnly("id1")
        assertThat(history2).extracting("version").containsExactly(1, 2)
        assertThat(history2).extracting("dateUpdated").doesNotHaveDuplicates()
    }

    @Test
    fun getHistoryNotExists() {
        assertThat(entryService.getEntryVersions("invalid")).isEmpty()
    }

}