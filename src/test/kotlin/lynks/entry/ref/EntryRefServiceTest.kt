package lynks.entry.ref

import lynks.common.DatabaseTest
import lynks.common.EntryId
import lynks.common.EntryType
import lynks.util.TEST_USER
import lynks.util.createDummyEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EntryRefServiceTest : DatabaseTest() {

    private val entryRefService = EntryRefService()

    @BeforeEach
    fun setup() {
        createDummyEntry("id1", "link1", "link content", EntryType.LINK)
        createDummyEntry("id2", "note1", "note content", EntryType.NOTE)
        createDummyEntry("id3", "note2", "note content second", EntryType.NOTE)
        createDummyEntry("id4", "snippet1", "snippet text", EntryType.SNIPPET)
    }

    @Test
    fun testSetAndGetEntryRefs() {
        entryRefService.setEntryRefs(EntryId("id1"), listOf("id2", "id4"), "id1")
        entryRefService.setEntryRefs(EntryId("id4"), listOf("id1", "id2"), "id4")
        val refs = entryRefService.getRefsForEntry(TEST_USER, EntryId("id1"))
        assertThat(refs.outbound).extracting<EntryId> { it.entryId }
            .containsOnly(EntryId("id2"), EntryId("id4"))
        assertThat(refs.outbound).extracting("title").containsOnly("note1", "snippet1")
        assertThat(refs.outbound).extracting("entryType").containsOnly(EntryType.NOTE, EntryType.SNIPPET)
        assertThat(refs.inbound).extracting<EntryId> { it.entryId }
            .containsOnly(EntryId("id4"))
        assertThat(refs.inbound).extracting("title").containsOnly("snippet1")
        assertThat(refs.inbound).extracting("entryType").containsOnly(EntryType.SNIPPET)

        val refs2 = entryRefService.getRefsForEntry(TEST_USER, EntryId("id4"))
        assertThat(refs2.outbound).extracting<EntryId> { it.entryId }
            .containsOnly(EntryId("id1"), EntryId("id2"))
        assertThat(refs2.outbound).extracting("title").containsOnly("link1", "note1")
        assertThat(refs2.outbound).extracting("entryType").containsOnly(EntryType.LINK, EntryType.NOTE)
        assertThat(refs2.inbound).extracting<EntryId> { it.entryId }
            .containsOnly(EntryId("id1"))
        assertThat(refs2.inbound).extracting("title").containsOnly("link1")
        assertThat(refs2.inbound).extracting("entryType").containsOnly(EntryType.LINK)

        assertThat(entryRefService.deleteOrigin("id1")).isEqualTo(2)
        val refs3 = entryRefService.getRefsForEntry(TEST_USER, EntryId("id1"))
        assertThat(refs3.outbound).isEmpty()
        assertThat(refs3.inbound).hasSize(1)
    }

}
