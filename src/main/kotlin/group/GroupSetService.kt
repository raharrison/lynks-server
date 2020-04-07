package group

import common.exception.InvalidModelException

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

    fun subtrees(tagId: String?, collectionId: String?): GroupSet {
        val tags = if (tagId != null) tagService.subtree(tagId) else emptyList()
        val collections = if (collectionId != null) collectionService.subtree(collectionId) else emptyList()
        return GroupSet(tags, collections)
    }


}