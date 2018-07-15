package util

import comment.Comment
import comment.Comments
import common.BaseEntries
import common.BaseProperties
import common.Link
import common.Note
import org.jetbrains.exposed.sql.ResultRow
import resource.Resource
import resource.Resources
import tag.Tag
import tag.Tags

object RowMapper {

    fun toLink(table: BaseEntries, row: ResultRow, tagResolver: (String) -> List<Tag>): Link =
            Link(
                    id = row[table.id],
                    title = row[table.title],
                    url = row[table.plainContent]!!,
                    source = row[table.src],
                    content = row[table.content],
                    dateUpdated = row[table.dateUpdated],
                    tags = tagResolver(row[table.id]),
                    props = row[table.props] ?: BaseProperties(),
                    version = row[table.version]
            )

    fun toNote(table: BaseEntries, row: ResultRow, tagResolver: (String) -> List<Tag>): Note =
            Note(
                    id = row[table.id],
                    title = row[table.title],
                    plainText = row[table.plainContent]!!,
                    markdownText = row[table.content]!!,
                    dateUpdated = row[table.dateUpdated],
                    tags = tagResolver(row[table.id]),
                    props = row[table.props] ?: BaseProperties(),
                    version = row[table.version]
            )

    fun toComment(row: ResultRow): Comment =
            Comment(
                    id = row[Comments.id],
                    entryId = row[Comments.entryId],
                    plainText = row[Comments.plainText],
                    markdownText = row[Comments.markdownText],
                    dateCreated = row[Comments.dateCreated]
            )

    fun toTag(row: ResultRow, childrenResolver: (String) -> MutableSet<Tag>): Tag {
        return Tag(
                id = row[Tags.id],
                name = row[Tags.name],
                children = childrenResolver(row[Tags.id]),
                dateUpdated = row[Tags.dateUpdated]
        )
    }

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
