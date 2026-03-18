package lynks.group

import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TagService : GroupService<Tag, NewTag>(GroupType.TAG) {

    override fun toInsert(gid: String, entity: NewTag): Groups.(InsertStatement<*>) -> Unit = {
        val time = OffsetDateTime.now(ZoneOffset.UTC)
        it[id] = gid
        it[name] = entity.name
        it[type] = GroupType.TAG
        it[dateCreated] = time
        it[dateUpdated] = time
    }

    override fun toUpdate(entity: NewTag): Groups.(UpdateBuilder<*>) -> Unit = {
        it[name] = entity.name
        it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
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

    override fun toCreateModel(name: String): NewTag = NewTag(name = name)

    override fun extractParentId(entity: NewTag): String? = null

}
