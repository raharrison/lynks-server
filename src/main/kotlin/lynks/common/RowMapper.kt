package lynks.common

import lynks.comment.Comment
import lynks.comment.Comments
import lynks.group.Collection
import lynks.group.Tag
import lynks.resource.Resource
import lynks.resource.Resources
import org.jetbrains.exposed.sql.ResultRow

object RowMapper {

    fun toLink(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): Link {
        return Link(
            id = row[table.id],
            title = row[table.title],
            url = row[table.plainContent]!!,
            source = row[table.src],
            content = row[table.content],
            dateCreated = row[table.dateCreated],
            dateUpdated = row[table.dateUpdated],
            tags = tags,
            collections = collections,
            props = row[table.props] ?: BaseProperties(),
            version = row[table.version],
            starred = row[table.starred],
            thumbnailId = row[table.thumbnailId]
        )
    }

    fun toSlimLink(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): SlimLink {
        return SlimLink(
            id = row[table.id],
            title = row[table.title],
            source = row[table.src],
            dateUpdated = row[table.dateUpdated],
            tags = tags,
            collections = collections,
            starred = row[table.starred],
            thumbnailId = row[table.thumbnailId]
        )
    }

    fun toNote(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): Note {
        return Note(
            id = row[table.id],
            title = row[table.title],
            plainText = row[table.plainContent]!!,
            markdownText = row[table.content]!!,
            dateCreated = row[table.dateCreated],
            dateUpdated = row[table.dateUpdated],
            tags = tags,
            collections = collections,
            props = row[table.props] ?: BaseProperties(),
            version = row[table.version],
            starred = row[table.starred]
        )
    }

    fun toSlimNote(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): SlimNote {
        return SlimNote(
            id = row[table.id],
            title = row[table.title],
            dateUpdated = row[table.dateUpdated],
            tags = tags,
            collections = collections,
            starred = row[table.starred]
        )
    }

    fun toFact(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): Fact {
        return Fact(
            id = row[table.id],
            plainText = row[table.plainContent]!!,
            markdownText = row[table.content]!!,
            dateCreated = row[table.dateCreated],
            dateUpdated = row[table.dateUpdated],
            tags = tags,
            collections = collections,
            props = row[table.props] ?: BaseProperties(),
            version = row[table.version],
            starred = row[table.starred]
        )
    }

    fun toSlimFact(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): SlimFact {
        return SlimFact(
            id = row[table.id],
            markdownText = row[table.content]!!,
            dateUpdated = row[table.dateUpdated],
            tags = tags,
            collections = collections,
            starred = row[table.starred]
        )
    }

    fun toComment(row: ResultRow): Comment =
            Comment(
                    id = row[Comments.id],
                    entryId = row[Comments.entryId],
                    plainText = row[Comments.plainText],
                    markdownText = row[Comments.markdownText],
                    dateCreated = row[Comments.dateCreated],
                    dateUpdated = row[Comments.dateUpdated]
            )

    fun toResource(row: ResultRow): Resource {
        return Resource(
                id = row[Resources.id],
                entryId = row[Resources.entryId],
                name = row[Resources.fileName],
                extension = row[Resources.extension],
                type = row[Resources.type],
                size = row[Resources.size],
                dateCreated = row[Resources.dateCreated],
                dateUpdated = row[Resources.dateUpdated]
        )
    }
}
