package lynks.group

import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class TagService : GroupService<Tag, NewTag>(GroupType.TAG) {

    override fun toInsert(eId: String, entity: NewTag): Groups.(InsertStatement<*>) -> Unit = {
        val time = System.currentTimeMillis()
        it[id] = eId
        it[name] = entity.name
        it[type] = GroupType.TAG
        it[dateCreated] = time
        it[dateUpdated] = time
    }

    override fun toUpdate(entity: NewTag): Groups.(UpdateBuilder<*>) -> Unit = {
        it[name] = entity.name
        it[dateUpdated] = System.currentTimeMillis()
    }

    override fun toModel(row: GroupRow, children: MutableSet<Tag>): Tag {
        return Tag(
            id = row.id,
            name = row.name,
            path = row.name,
            dateCreated = row.dateCreated,
            dateUpdated = row.dateUpdated
        )
    }

    override fun extractParentId(entity: NewTag): String? = null

}
