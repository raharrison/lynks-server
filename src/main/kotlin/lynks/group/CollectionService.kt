package lynks.group

import lynks.common.exception.InvalidModelException
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class CollectionService : GroupService<Collection, NewCollection>(GroupType.COLLECTION) {

    override fun add(group: NewCollection): Collection {
        if (group.name.contains("/")) {
            if (group.parentId != null) {
                throw InvalidModelException("New collection cannot contain slashes if parent is defined")
            }
            val parents = group.name.split("/").toMutableList()
            val name = parents.removeLast()
            if (name.isEmpty() || parents.any { it.isEmpty() }) {
                throw InvalidModelException("Invalid group format, expected: 'parent1/parent2/group1'")
            }
            val parent = getOrCreateFromPath(parents)
            return super.add(group.copy(name = name, parentId = parent.id))
        }
        return super.add(group)
    }

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

    override fun toModel(row: GroupRow, children: MutableSet<Collection>): Collection {
        return Collection(
            id = row.id,
            name = row.name,
            path = row.name,
            children = children,
            dateCreated = row.dateCreated,
            dateUpdated = row.dateUpdated
        )
    }

    override fun toCreateModel(name: String): NewCollection = NewCollection(name = name)

    override fun extractParentId(entity: NewCollection): String? = entity.parentId

}
