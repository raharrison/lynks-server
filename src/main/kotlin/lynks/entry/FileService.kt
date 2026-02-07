package lynks.entry

import lynks.common.*
import lynks.db.EntryRepository
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.resource.ResourceManager
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.selectAll

class FileService(
    groupSetService: GroupSetService, entryAuditService: EntryAuditService, resourceManager: ResourceManager
) : EntryRepository<File, SlimFile, NewFile>(groupSetService, entryAuditService, resourceManager) {

    override fun toModel(row: ResultRow, groups: GroupSet, table: BaseEntries): File {
        return RowMapper.toFile(table, row, groups.tags, groups.collections)
    }

    override fun toSlimModel(row: ResultRow, groups: GroupSet, table: BaseEntries): SlimFile {
        return RowMapper.toSlimFile(table, row, groups.tags, groups.collections)
    }

    override fun getBaseQuery(base: ColumnSet, where: BaseEntries): Query {
        return base.selectAll().where { where.type eq EntryType.FILE }
    }

    override val slimColumnSet: List<Column<*>> =
        listOf(Entries.id, Entries.title, Entries.dateUpdated, Entries.starred)

    override fun toInsert(eId: EntryId, entry: NewFile): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        val time = System.currentTimeMillis()
        it[id] = eId.value
        it[title] = entry.title
        it[src] = "me"
        it[type] = EntryType.FILE
        it[dateCreated] = time
        it[dateUpdated] = time
    }

    override fun toUpdate(entry: NewFile): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[title] = entry.title
        it[dateUpdated] = System.currentTimeMillis()
    }

    override fun toUpdate(entry: File): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[title] = entry.title
        it[props] = entry.props
    }

}
