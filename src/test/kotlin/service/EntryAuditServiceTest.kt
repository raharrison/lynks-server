package service

import common.DatabaseTest
import common.EntryType
import entry.EntryAuditService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import util.createDummyEntry

class EntryAuditServiceTest: DatabaseTest() {

    private val entryAuditService = EntryAuditService()

    @BeforeEach
    fun insertEntry() {
        createDummyEntry("e1", "title", "content", EntryType.NOTE)
    }

    @Test
    fun testCreateAndGetAuditItem() {
        entryAuditService.acceptAuditEvent("e1", "source", "message")
        entryAuditService.acceptAuditEvent("e1", "source2", "message2")

        val entryAudit = entryAuditService.getEntryAudit("e1")
        assertThat(entryAudit).hasSize(2)
        assertThat(entryAudit).extracting("entryId").containsOnly("e1")
        assertThat(entryAudit).extracting("src").containsExactly("source", "source2")
        assertThat(entryAudit).extracting("details").containsExactly("message", "message2")
    }

    @Test
    fun testGetAuditItemsNotExists() {
        assertThat(entryAuditService.getEntryAudit("invalid")).isEmpty()
    }
}