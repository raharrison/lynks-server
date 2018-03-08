package util

import model.Comment
import model.Comments
import model.Tag
import model.Tags
import org.jetbrains.exposed.sql.ResultRow

object RowMapper {

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
}