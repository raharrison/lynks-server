package group

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class TagService : GroupService<Tag, NewTag, Tags>(Tags) {

    override fun toInsert(eId: String, entity: NewTag): Tags.(InsertStatement<*>) -> Unit = {
        val time = System.currentTimeMillis()
        it[Tags.id] = eId
        it[Tags.name] = entity.name
        it[Tags.dateCreated] = time
        it[Tags.dateUpdated] = time
    }

    override fun toUpdate(entity: NewTag): Tags.(UpdateBuilder<*>) -> Unit = {
        it[Tags.name] = entity.name
        it[Tags.dateUpdated] = System.currentTimeMillis()
    }

    override fun toModel(row: ResultRow): Tag {
        return Tag(
                id = row[Tags.id],
                name = row[Tags.name],
                dateCreated = row[Tags.dateCreated],
                dateUpdated = row[Tags.dateUpdated]
        )
    }

    override fun extractParentId(entity: NewTag): String? = null

}