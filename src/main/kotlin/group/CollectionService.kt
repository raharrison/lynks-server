package group

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class CollectionService : GroupService<Collection, NewCollection, Collections>(Collections, EntryCollections) {

    override fun toInsert(eId: String, entity: NewCollection): Collections.(InsertStatement<*>) -> Unit = {
        it[Collections.id] = eId
        it[Collections.name] = entity.name
        it[Collections.parentId] = entity.parentId
        it[Collections.dateCreated] = System.currentTimeMillis()
        it[Collections.dateUpdated] = System.currentTimeMillis()
    }

    override fun toUpdate(entity: NewCollection): Collections.(UpdateBuilder<*>) -> Unit = {
        it[Collections.name] = entity.name
        it[Collections.parentId] = entity.parentId
        it[Collections.dateUpdated] = System.currentTimeMillis()
    }

    override fun toModel(row: ResultRow): Collection {
        return Collection(
                id = row[Collections.id],
                name = row[Collections.name],
                children = getGroupChildren(row[Collections.id]),
                dateCreated = row[Collections.dateCreated],
                dateUpdated = row[Collections.dateUpdated]
        )
    }

    override fun extractParentId(entity: NewCollection): String? = entity.parentId

}