package lynks.entry

import lynks.common.*
import lynks.db.EntryRepository
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.resource.ResourceManager
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder

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
        return base.select { where.type eq EntryType.FILE }
    }

    override val slimColumnSet: List<Column<*>> =
        listOf(Entries.id, Entries.title, Entries.dateUpdated, Entries.starred)

    override fun toInsert(eId: String, entry: NewFile): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        val time = System.currentTimeMillis()
        it[id] = eId
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
