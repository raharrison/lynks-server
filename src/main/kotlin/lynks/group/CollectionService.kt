package lynks.group

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class CollectionService : GroupService<Collection, NewCollection>(GroupType.COLLECTION) {

    override fun toInsert(eId: String, entity: NewCollection): Groups.(InsertStatement<*>) -> Unit = {
        val time = System.currentTimeMillis()
        it[id] = eId
        it[name] = entity.name
        it[type] = GroupType.COLLECTION
        it[parentId] = entity.parentId
        it[dateCreated] = time
        it[dateUpdated] = time
    }

    override fun toUpdate(entity: NewCollection): Groups.(UpdateBuilder<*>) -> Unit = {
        it[name] = entity.name
        it[parentId] = entity.parentId
        it[dateUpdated] = System.currentTimeMillis()
    }

    override fun toModel(row: ResultRow): Collection {
        return Collection(
            id = row[Groups.id],
            name = row[Groups.name],
            path = null,
            children = getGroupChildren(row[Groups.id]),
            dateCreated = row[Groups.dateCreated],
            dateUpdated = row[Groups.dateUpdated]
        )
    }

    override fun extractParentId(entity: NewCollection): String? = entity.parentId

}
