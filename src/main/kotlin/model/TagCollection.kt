package model

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap

class TagCollection {

    // tag : [parents]

    // TODO: change to set??
    private val tagTree: Multimap<Tag, Tag> = HashMultimap.create()
    private val tagLookup: MutableMap<String, Tag> = mutableMapOf()

    fun build(tags: Collection<Tag>) {
        tagTree.clear()
        tagLookup.clear()
        tags.forEach { processTag(it) }
    }

    private fun processTag(tag: Tag) {
        tagLookup[tag.id] = tag
        for (child in tag.children) {
            tagTree.put(tag, child)
            processTag(child)
        }
    }

    fun tag(id: String): Tag? = tagLookup[id]

    fun tagsIn(ids: Collection<String>): List<Tag> = ids.map { tagLookup.getValue(it) }

    fun add(tag: Tag, parent: String?): Tag {
        tagLookup[tag.id] = tag
        parent?.also {
            tagTree.put(tag, tag(parent))
            tagLookup[it]?.children?.add(tag)
        }
        return tag
    }

    fun subtree(id: String): MutableCollection<Tag> {
        val tag = tag(id)
        return tagTree[tag].apply { add(tag) }
    }

    fun all(): Collection<Tag> = tagLookup.values

}