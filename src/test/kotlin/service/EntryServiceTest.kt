package service

import common.*
import common.page.PageRequest
import common.page.SortDirection
import entry.EntryAuditService
import entry.EntryService
import group.CollectionService
import group.GroupSetService
import group.TagService
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import resource.ResourceManager
import util.createDummyEntry
import util.updateDummyEntry

class EntryServiceTest: DatabaseTest() {

    private val tagService = TagService()
    private val collectionService = CollectionService()
    private val groupSetService = GroupSetService(tagService, collectionService)
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private val resourceManager = mockk<ResourceManager>()
    private val entryService = EntryService(groupSetService, entryAuditService, resourceManager)

    @BeforeEach
    fun createEntries() {
        createDummyEntry("id1", "link1", "link content", EntryType.LINK)
        Thread.sleep(10)// prevent having same creation timestamp
        createDummyEntry("id2", "note1", "note content", EntryType.NOTE)
        Thread.sleep(10)
        createDummyEntry("id3", "note2", "note content second", EntryType.NOTE)
        Thread.sleep(10)
        createDummyEntry("id4", "fact1", "fact text", EntryType.FACT)
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

        val retrieved3 = entryService.get("id4") as Fact
        assertThat(retrieved3.id).isEqualTo("id4")
        assertThat(retrieved3.plainText).isEqualTo("fact text")
        assertThat(retrieved3.type).isEqualTo(EntryType.FACT)
    }

    @Test
    fun testGetAll() {
        val retrieved = entryService.get()
        assertThat(retrieved.content).hasSize(4)
        assertThat(retrieved.page).isEqualTo(1L)
        assertThat(retrieved.total).isEqualTo(4)
        assertThat(retrieved.content).extracting("id").containsExactlyInAnyOrder("id1", "id2", "id3", "id4")
        assertThat(retrieved.content).hasAtLeastOneElementOfType(SlimNote::class.java)
        assertThat(retrieved.content).hasAtLeastOneElementOfType(SlimLink::class.java)
        assertThat(retrieved.content).hasAtLeastOneElementOfType(SlimFact::class.java)
    }

    @Test
    fun testGetByIds() {
        val retrieved = entryService.get(listOf("id2", "id3"))
        assertThat(retrieved.content).hasSize(2)
        assertThat(retrieved.page).isEqualTo(1L)
        assertThat(retrieved.total).isEqualTo(2)
        assertThat(retrieved.content).extracting("id").containsExactlyInAnyOrder("id2", "id3")
    }

    @Test
    fun testGetByIdsAndPaged() {
        val retrieved = entryService.get(listOf("id1", "id2", "id3"), PageRequest(2, 1))
        assertThat(retrieved.content).hasSize(1)
        assertThat(retrieved.page).isEqualTo(2L)
        assertThat(retrieved.size).isEqualTo(1)
        assertThat(retrieved.total).isEqualTo(3)
        assertThat(retrieved.content).extracting("id").containsExactlyInAnyOrder("id2")
    }

    @Test
    fun testPaging() {
        // order by date updated
        val retrieved = entryService.get(PageRequest(1, 1))
        assertThat(retrieved.content).hasSize(1)
        assertThat(retrieved.page).isEqualTo(1L)
        assertThat(retrieved.total).isEqualTo(4)
        assertThat(retrieved.content).extracting("id").containsExactly("id4")
        assertThat(retrieved.content).hasOnlyElementsOfType(SlimFact::class.java)

        val retrieved2 = entryService.get(PageRequest(4, 1))
        assertThat(retrieved2.content).hasSize(1)
        assertThat(retrieved2.page).isEqualTo(4L)
        assertThat(retrieved2.size).isEqualTo(1)
        assertThat(retrieved2.total).isEqualTo(4)
        assertThat(retrieved2.content).extracting("id").containsExactly("id1")
        assertThat(retrieved2.content).hasOnlyElementsOfType(SlimLink::class.java)
    }

    @Test
    fun testSearchSortOrdering() {
        val retrieved = entryService.get(PageRequest(0, 10, sort = "dateCreated", direction = SortDirection.ASC))
        assertThat(retrieved.content).extracting("id").containsExactly("id1", "id2", "id3", "id4")

        val retrieved2 = entryService.get(PageRequest(0, 10, sort = "dateCreated", direction = SortDirection.DESC))
        assertThat(retrieved2.content).extracting("id").containsExactly("id4", "id3", "id2", "id1")
    }

    @Test
    fun testSearchTitle() {
        val entries = entryService.search("note").content
        assertThat(entries).hasSize(2)
        assertThat(entries).extracting("id").containsExactlyInAnyOrder("id2", "id3")
        assertThat(entries).hasOnlyElementsOfType(SlimNote::class.java)

        val entries2 = entryService.search("link").content
        assertThat(entries2).hasSize(1)
        assertThat(entries2).extracting("id").containsExactly("id1")
        assertThat(entries2).hasOnlyElementsOfType(SlimLink::class.java)
    }

    @Test
    fun testSearchMultipleResults() {
        val entries = entryService.search("content").content
        assertThat(entries).hasSize(3)
        assertThat(entries).extracting("id").containsExactlyInAnyOrder("id1", "id2", "id3")
        assertThat(entries).hasAtLeastOneElementOfType(SlimNote::class.java)
        assertThat(entries).hasAtLeastOneElementOfType(SlimLink::class.java)
    }

    @Test
    fun testSearchNoResults() {
        val entries = entryService.search("nothing")
        assertThat(entries.content).isEmpty()
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

        verify(exactly = 2) { entryAuditService.acceptAuditEvent("id1", any(), any()) }
    }

    @Test
    fun testSetStarInvalidEntry() {
        assertThat(entryService.star("invalid", true)).isNull()
        assertThat(entryService.star("invalid", false)).isNull()
        verify(exactly = 0) { entryAuditService.acceptAuditEvent("invalid", any(), any()) }
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
