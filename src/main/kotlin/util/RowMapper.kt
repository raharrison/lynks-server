package util

import comment.Comment
import comment.Comments
import common.Entries
import common.Link
import common.Note
import org.jetbrains.exposed.sql.ResultRow
import resource.Resource
import resource.Resources
import tag.Tag
import tag.Tags
import java.nio.file.Path

object RowMapper {

    fun toLink(row: ResultRow, tagResolver: (String) -> List<Tag>): Link =
            Link(
                    id = row[Entries.id],
                    title = row[Entries.title],
                    url = row[Entries.plainContent]!!,
                    source = row[Entries.src],
                    dateUpdated = row[Entries.dateUpdated],
                    tags = tagResolver(row[Entries.id])
            )

    fun toNote(row: ResultRow, tagResolver: (String) -> List<Tag>): Note =
            Note(
                    id = row[Entries.id],
                    title = row[Entries.title],
                    plainText = row[Entries.plainContent]!!,
                    markdownText = row[Entries.content]!!,
                    dateUpdated = row[Entries.dateUpdated],
                    tags = tagResolver(row[Entries.id])
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

    fun toFile(row: ResultRow, pathBuilder: (String, String, String) -> Path): Resource {
        return Resource(
                id = row[Resources.id],
                entryId = row[Resources.entryId],
                name = row[Resources.fileName],
                extension = row[Resources.extension],
                path = pathBuilder(row[Resources.entryId], row[Resources.id], row[Resources.extension]).toString(),
                type = row[Resources.type],
                size = row[Resources.size],
                dateCreated = row[Resources.dateCreated],
                dateUpdated = row[Resources.dateUpdated]
        )
    }
}