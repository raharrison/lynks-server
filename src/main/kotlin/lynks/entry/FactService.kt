package lynks.entry

import lynks.common.*
import lynks.db.EntryRepository
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.resource.ResourceManager
import lynks.resource.TempImageMarkdownVisitor
import lynks.util.markdown.MarkdownUtils
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class FactService(
    groupSetService: GroupSetService, entryAuditService: EntryAuditService, resourceManager: ResourceManager
) : EntryRepository<Fact, SlimFact, NewFact>(groupSetService, entryAuditService, resourceManager) {

    override fun postprocess(eid: String, entry: NewFact): Fact {
        val (replaced, markdown) = MarkdownUtils.visitAndReplaceNodes(entry.plainText, TempImageMarkdownVisitor(eid, resourceManager))
        if(replaced > 0) {
            return update(entry.copy(id = eid, plainText = markdown), newVersion = false)!!
        }
        return super.postprocess(eid, entry)
    }

    override fun toModel(row: ResultRow, groups: GroupSet, table: BaseEntries): Fact {
        return RowMapper.toFact(table, row, groups.tags, groups.collections)
    }

    override fun toSlimModel(row: ResultRow, groups: GroupSet, table: BaseEntries): SlimFact {
        return RowMapper.toSlimFact(table, row, groups.tags, groups.collections)
    }

    override fun getBaseQuery(base: ColumnSet, where: BaseEntries): Query {
        return base.select { where.type eq EntryType.FACT }
    }

    override fun toInsert(eId: String, entry: NewFact): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        val time = System.currentTimeMillis()
        it[id] = eId
        it[title] = "Fact"
        it[plainContent] = entry.plainText
        it[content] = MarkdownUtils.convertToMarkdown(entry.plainText)
        it[src] = "Me"
        it[type] = EntryType.FACT
        it[dateCreated] = time
        it[dateUpdated] = time
    }

    override fun toUpdate(entry: NewFact): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[plainContent] = entry.plainText
        it[content] = MarkdownUtils.convertToMarkdown(entry.plainText)
        it[dateUpdated] = System.currentTimeMillis()
    }

    override fun toUpdate(entry: Fact): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[plainContent] = entry.plainText
        it[content] = MarkdownUtils.convertToMarkdown(entry.plainText)
        it[props] = entry.props
    }
}
