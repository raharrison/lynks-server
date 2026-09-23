package lynks.entry

import lynks.common.*
import lynks.db.EntryRepository
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.resource.ResourceManager
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import java.time.OffsetDateTime
import java.time.ZoneOffset

class FileService(
    groupSetService: GroupSetService, entryAuditService: EntryAuditService, resourceManager: ResourceManager
) : EntryRepository<File, SlimFile, NewFile>(groupSetService, entryAuditService, resourceManager) {

    override fun toModel(row: ResultRow, groups: GroupSet, table: BaseEntries): File {
        return RowMapper.toFile(table, row, groups.tags, groups.collections)
    }

    override fun toSlimModel(row: ResultRow, groups: GroupSet, table: BaseEntries): SlimFile {
        return RowMapper.toSlimFile(table, row, groups.tags, groups.collections)
    }

    override fun typeCondition(table: BaseEntries): Op<Boolean> = table.type eq EntryType.FILE

    override val slimColumnSet: List<Column<*>> =
        listOf(Entries.id, Entries.title, Entries.dateUpdated, Entries.starred)

    override fun toInsert(userId: UserId, eId: EntryId, entry: NewFile): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        val time = OffsetDateTime.now(ZoneOffset.UTC)
        it[id] = eId.value
        it[title] = entry.title
        it[src] = "me"
        it[type] = EntryType.FILE
        it[dateCreated] = time
        it[dateUpdated] = time
    }

    override fun toUpdate(userId: UserId, entry: NewFile): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[title] = entry.title
        it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
    }

    override fun toNewEntry(userId: UserId, entry: File) = NewFile(
        entry.id, entry.title, entry.tags.map { it.id }, entry.collections.map { it.id }
    )

    override fun toUpdate(userId: UserId, entry: File): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[title] = entry.title
        it[props] = entry.props
    }

}
