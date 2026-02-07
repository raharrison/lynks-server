package lynks.common

import lynks.comment.Comment
import lynks.comment.Comments
import lynks.group.Collection
import lynks.group.Tag
import lynks.notify.Notification
import lynks.notify.Notifications
import lynks.resource.Resource
import lynks.resource.ResourceVersions
import lynks.resource.Resources
import lynks.user.ActivityLogItem
import org.jetbrains.exposed.v1.core.ResultRow

object RowMapper {

    fun toLink(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): Link {
        return Link(
            id = EntryId(row[table.id]),
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
            thumbnailId = row[table.thumbnailId]?.let { ResourceId(it) },
            read = row[table.read] ?: false
        )
    }

    fun toSlimLink(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): SlimLink {
        return SlimLink(
            id = EntryId(row[table.id]),
            title = row[table.title],
            source = row[table.src],
            dateUpdated = row[table.dateUpdated],
            tags = tags,
            collections = collections,
            starred = row[table.starred],
            thumbnailId = row[table.thumbnailId]?.let { ResourceId(it) },
            read = row[table.read] ?: false
        )
    }

    fun toNote(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): Note {
        return Note(
            id = EntryId(row[table.id]),
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
            id = EntryId(row[table.id]),
            title = row[table.title],
            dateUpdated = row[table.dateUpdated],
            tags = tags,
            collections = collections,
            starred = row[table.starred]
        )
    }

    fun toSnippet(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): Snippet {
        return Snippet(
            id = EntryId(row[table.id]),
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

    fun toSlimSnippet(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): SlimSnippet {
        return SlimSnippet(
            id = EntryId(row[table.id]),
            markdownText = row[table.content]!!,
            dateUpdated = row[table.dateUpdated],
            tags = tags,
            collections = collections,
            starred = row[table.starred]
        )
    }

    fun toFile(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): File {
        return File(
            id = EntryId(row[table.id]),
            title = row[table.title],
            dateCreated = row[table.dateCreated],
            dateUpdated = row[table.dateUpdated],
            tags = tags,
            collections = collections,
            props = row[table.props] ?: BaseProperties(),
            version = row[table.version],
            starred = row[table.starred]
        )
    }

    fun toSlimFile(table: BaseEntries, row: ResultRow, tags: List<Tag>, collections: List<Collection>): SlimFile {
        return SlimFile(
            id = EntryId(row[table.id]),
            title = row[table.title],
            dateUpdated = row[table.dateUpdated],
            tags = tags,
            collections = collections,
            starred = row[table.starred]
        )
    }

    fun toComment(row: ResultRow): Comment =
        Comment(
            id = CommentId(row[Comments.id]),
            entryId = EntryId(row[Comments.entryId]),
            plainText = row[Comments.plainText],
            markdownText = row[Comments.markdownText],
            dateCreated = row[Comments.dateCreated],
            dateUpdated = row[Comments.dateUpdated]
        )

    fun toResource(row: ResultRow): Resource {
        return Resource(
            id = ResourceId(row[ResourceVersions.id]),
            parentId = row[Resources.id],
            entryId = EntryId(row[Resources.entryId]),
            version = row[ResourceVersions.version],
            name = row[Resources.fileName],
            extension = row[Resources.extension],
            type = row[Resources.type],
            size = row[ResourceVersions.size],
            dateCreated = row[ResourceVersions.dateCreated]
        )
    }

    fun toNotification(row: ResultRow): Notification {
        return Notification(
            id = NotificationId(row[Notifications.notificationId]),
            type = row[Notifications.notificationType],
            message = row[Notifications.message],
            read = row[Notifications.read],
            entryId = row[Notifications.entryId]?.let { EntryId(it) },
            entryType = row[Entries.type],
            entryTitle = row[Entries.title],
            dateCreated = row[Notifications.dateCreated]
        )
    }

    fun toActivityLogItem(row: ResultRow): ActivityLogItem {
        return ActivityLogItem(
            id = row[EntryAudit.auditId],
            entryId = EntryId(row[EntryAudit.entryId]),
            src = row[EntryAudit.src],
            details = row[EntryAudit.details],
            entryType = row[Entries.type],
            entryTitle = row[Entries.title],
            timestamp = row[EntryAudit.timestamp]
        )
    }
}
