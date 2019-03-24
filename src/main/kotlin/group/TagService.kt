package group

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class TagService : GroupService<Tag, NewTag>(GroupType.TAG) {

    override fun toInsert(eId: String, entity: NewTag): Groups.(InsertStatement<*>) -> Unit = {
        val time = System.currentTimeMillis()
        it[Groups.id] = eId
        it[Groups.name] = entity.name
        it[Groups.type] = GroupType.TAG
        it[Groups.dateCreated] = time
        it[Groups.dateUpdated] = time
    }

    override fun toUpdate(entity: NewTag): Groups.(UpdateBuilder<*>) -> Unit = {
        it[Groups.name] = entity.name
        it[Groups.dateUpdated] = System.currentTimeMillis()
    }

    override fun toModel(row: ResultRow): Tag {
        return Tag(
                id = row[Groups.id],
                name = row[Groups.name],
                dateCreated = row[Groups.dateCreated],
                dateUpdated = row[Groups.dateUpdated]
        )
    }

    override fun extractParentId(entity: NewTag): String? = null

}