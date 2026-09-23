package lynks.group

import lynks.common.UserId
import lynks.common.exception.InvalidModelException

class GroupSetService(private val tagService: TagService, private val collectionService: CollectionService) {

    fun getIn(userId: UserId, ids: List<String>): GroupSet {
        return GroupSet(tagService.getIn(userId, ids), collectionService.getIn(userId, ids))
    }

    fun assertGroups(userId: UserId, tagIds: List<String>, collectionIds: List<String>) {
        tagIds.forEach {
            if (tagService.get(userId, it) == null)
                throw InvalidModelException("Unknown tag: $it")
        }
        collectionIds.forEach {
            if (collectionService.get(userId, it) == null)
                throw InvalidModelException("Unknown collection: $it")
        }
    }

    fun subtrees(userId: UserId, tagIds: List<String>, collectionIds: List<String>): GroupSet {
        val tags = tagIds.flatMap { tagService.subtree(userId, it) }
        val collections = collectionIds.flatMap { collectionService.subtree(userId, it) }
        return GroupSet(tags, collections)
    }

    fun matchWithContent(userId: UserId, content: String?): GroupSet {
        if (content == null) return GroupSet()
        val words = content.lowercase().split(" ").toSet()
        val tagsByName = tagService.sequence(userId).associateBy { it.name.lowercase() }
        val collectionsByName = collectionService.sequence(userId).associateBy { it.name.lowercase() }
        return GroupSet(
            words.mapNotNull { tagsByName[it] },
            words.mapNotNull { collectionsByName[it] }
        )
    }

}
