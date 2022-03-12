package lynks.group

import lynks.common.exception.InvalidModelException

class GroupSetService(private val tagService: TagService, private val collectionService: CollectionService) {

    fun getIn(ids: List<String>): GroupSet {
        return GroupSet(tagService.getIn(ids), collectionService.getIn(ids))
    }

    fun assertGroups(tagIds: List<String>, collectionIds: List<String>) {
        tagIds.forEach {
            if (tagService.get(it) == null)
                throw InvalidModelException("Unknown tag: $it")
        }
        collectionIds.forEach {
            if (collectionService.get(it) == null)
                throw InvalidModelException("Unknown collection: $it")
        }
    }

    fun subtrees(tagIds: List<String>, collectionIds: List<String>): GroupSet {
        val tags = tagIds.flatMap { tagService.subtree(it) }
        val collections = collectionIds.flatMap { collectionService.subtree(it) }
        return GroupSet(tags, collections)
    }

    fun matchWithContent(content: String?): GroupSet {
        if(content == null) {
            return GroupSet()
        }
        val words = content.lowercase().split(" ").toSet()
        return GroupSet(
            tagService.sequence().filter { words.contains(it.name.lowercase()) }.toList(),
            collectionService.sequence().filter { words.contains(it.name.lowercase()) }.toList(),
        )
    }

}
