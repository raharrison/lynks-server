package lynks.service

import io.mockk.mockk
import io.mockk.verify
import lynks.common.*
import lynks.common.exception.InvalidModelException
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.entry.EntryAuditService
import lynks.entry.EntryService
import lynks.group.CollectionService
import lynks.group.GroupSetService
import lynks.group.TagService
import lynks.resource.ResourceManager
import lynks.util.createDummyCollection
import lynks.util.createDummyEntry
import lynks.util.createDummyTag
import lynks.util.updateDummyEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
        createDummyEntry("id4", "snippet1", "snippet text", EntryType.SNIPPET)
        Thread.sleep(10)
        createDummyEntry("id5", "file", "filename", EntryType.FILE)
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

        val retrieved3 = entryService.get("id4") as Snippet
        assertThat(retrieved3.id).isEqualTo("id4")
        assertThat(retrieved3.plainText).isEqualTo("snippet text")
        assertThat(retrieved3.type).isEqualTo(EntryType.SNIPPET)

        val retrieved4 = entryService.get("id5") as File
        assertThat(retrieved4.id).isEqualTo("id5")
        assertThat(retrieved4.title).isEqualTo("file")
        assertThat(retrieved4.type).isEqualTo(EntryType.FILE)
    }

    @Test
    fun testGetAll() {
        val retrieved = entryService.get()
        assertThat(retrieved.content).hasSize(5)
        assertThat(retrieved.page).isEqualTo(1L)
        assertThat(retrieved.total).isEqualTo(5)
        assertThat(retrieved.content).extracting("id").containsExactlyInAnyOrder("id1", "id2", "id3", "id4", "id5")
        assertThat(retrieved.content).hasAtLeastOneElementOfType(SlimNote::class.java)
        assertThat(retrieved.content).hasAtLeastOneElementOfType(SlimLink::class.java)
        assertThat(retrieved.content).hasAtLeastOneElementOfType(SlimSnippet::class.java)
        assertThat(retrieved.content).hasAtLeastOneElementOfType(SlimFile::class.java)
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
        assertThat(retrieved.total).isEqualTo(5)
        assertThat(retrieved.content).extracting("id").containsExactly("id5")
        assertThat(retrieved.content).hasOnlyElementsOfType(SlimFile::class.java)

        val retrieved2 = entryService.get(PageRequest(5, 1))
        assertThat(retrieved2.content).hasSize(1)
        assertThat(retrieved2.page).isEqualTo(5L)
        assertThat(retrieved2.size).isEqualTo(1)
        assertThat(retrieved2.total).isEqualTo(5)
        assertThat(retrieved2.content).extracting("id").containsExactly("id1")
        assertThat(retrieved2.content).hasOnlyElementsOfType(SlimLink::class.java)
    }

    @Test
    fun testRandomOrdering() {
        val retrieved = entryService.get(PageRequest(1, 5, sort = null, direction = SortDirection.RAND))
        assertThat(retrieved.content).hasSize(5)
        assertThat(retrieved.page).isEqualTo(1L)
        assertThat(retrieved.total).isEqualTo(5)
    }

    @Test
    fun testSearchSortOrdering() {
        val retrieved = entryService.get(PageRequest(0, 10, sort = "dateCreated", direction = SortDirection.ASC))
        assertThat(retrieved.content).extracting("id").containsExactly("id1", "id2", "id3", "id4", "id5")

        val retrieved2 = entryService.get(PageRequest(0, 10, sort = "dateCreated", direction = SortDirection.DESC))
        assertThat(retrieved2.content).extracting("id").containsExactly("id5", "id4", "id3", "id2", "id1")
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

    @Test
    fun testUpdateEntryGroups() {
        createDummyTag("t1", "tag1")
        createDummyCollection("c1", "col1")
        val original = entryService.get("id2")
        assertThat(original).isNotNull()
        assertThat(original?.tags).isEmpty()
        assertThat(original?.collections).isEmpty()
        val result = entryService.updateEntryGroups("id2", listOf("t1"), listOf("c1"))
        assertThat(result).isTrue()
        val updated = entryService.get("id2")
        assertThat(updated).isNotNull()
        assertThat(updated?.tags).extracting("id").containsOnly("t1")
        assertThat(updated?.collections).extracting("id").containsOnly("c1")
    }

    @Test
    fun testUpdateGroupsNotFound() {
        assertThrows<InvalidModelException> {
            entryService.updateEntryGroups("id1", listOf("t1"), emptyList())
        }
        assertThrows<InvalidModelException> {
            entryService.updateEntryGroups("id1", emptyList(), listOf("c1"))
        }
    }

    @Test
    fun testUpdateGroupsEntryNotFound() {
        createDummyTag("t1", "tag1")
        createDummyCollection("c1", "col1")
        val result = entryService.updateEntryGroups("invalid", listOf("t1"), listOf("c1"))
        assertThat(result).isFalse()
    }

}
