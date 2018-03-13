package util

import model.*
import org.jetbrains.exposed.sql.ResultRow

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

    fun toTag(row: ResultRow, childrenResolver: (String) -> MutableList<Tag>): Tag {
        return Tag(
                id = row[Tags.id],
                name = row[Tags.name],
                children = childrenResolver(row[Tags.id]),
                dateUpdated = row[Tags.dateUpdated]
        )
    }

    fun toFile(row: ResultRow, pathBuilder: (String, String, String) -> String): File {
        return File(
                id = row[Files.id],
                entryId = row[Files.entryId],
                name = row[Files.fileName],
                extension = row[Files.extension],
                path = pathBuilder(row[Files.entryId], row[Files.id], row[Files.extension]),
                type = row[Files.type],
                size = row[Files.size],
                dateCreated = row[Files.dateCreated],
                dateUpdated = row[Files.dateUpdated]
        )
    }
}