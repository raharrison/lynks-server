package tag

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap

class TagCollection {

    // tag : [children]

    private val tagTree: Multimap<Tag, Tag> = HashMultimap.create()
    private val tagLookup: MutableMap<String, Tag> = mutableMapOf()
    private val tagParents: MutableMap<String, Tag> = mutableMapOf()

    // tags are root nodes
    fun build(tags: Collection<Tag>) {
        tagTree.clear()
        tagLookup.clear()
        tagParents.clear()
        tags.forEach { processTag(it) }
    }

    private fun processTag(tag: Tag) {
        tagLookup[tag.id] = tag
        tagTree.putAll(tag, traverseChildren(tag, mutableSetOf()))
        for (child in tag.children) {
            tagParents[child.id] = tag
            processTag(child)
        }
    }

    private fun traverseChildren(tag: Tag, existing: MutableSet<Tag>): MutableSet<Tag> {
        existing.addAll(tag.children)
        for(child in tag.children) {
            existing.addAll(traverseChildren(child, existing))
        }
        return existing
    }

    private fun traverseParents(id: String, block: (Tag) -> Unit) {
        if(tagParents.containsKey(id)) {
            tagParents[id]?.also{
                block(tagParents[id]!!)
                traverseParents(it.id, block)
            }
        }
    }

    fun tag(id: String): Tag? = tagLookup[id]

    fun tagsIn(ids: Collection<String>): List<Tag> = ids.map { tagLookup[it] }.filterNotNull()

    fun add(tag: Tag, parent: String?): Tag {
        tagLookup[tag.id] = tag
        parent?.also {
            val ptag = tag(parent)!!
            tagTree.put(tag, ptag)
            ptag.children.add(tag)
            tagParents[tag.id] = ptag
        }
        traverseParents(tag.id, { tagTree.put(it, tag) })
        return tag
    }

    fun update(newParentId: String?, tag: Tag): Tag {
        val currentParentId = tagParents[tag.id]?.id
        if(currentParentId != newParentId) {
            // had a parent before so remove from children
            if(currentParentId != null) {
                val currentParent = tag(currentParentId)!!
                currentParent.children.remove(tag)
            }

            // add as child to new parent
            if(newParentId != null) {
                val newParent = tag(newParentId)!!
                newParent.children.add(tag)
            }

            // recursive update of parents
            build(all().toList())
        }
        val current = tag(tag.id)!!
        current.name = tag.name
        current.children = tag.children
        current.dateUpdated = tag.dateUpdated
        return current
    }

    fun subtree(id: String): MutableCollection<Tag> {
        val tag = tag(id)
        return tagTree[tag].also { if(tag != null) it.add(tag) }
    }

    fun rootTags(): Collection<Tag> = tagLookup.values.filter { !tagParents.containsKey(it.id) }

    fun all(): Collection<Tag> = tagLookup.values

    fun delete(id: String) {
        val tag = tagLookup[id]
        tagTree.removeAll(id)
        tagLookup.remove(id)
        tag?.children?.forEach{ delete(it.id) }
        traverseParents(id, { tagTree.remove(it, tag) })
        tagParents[id]?.also {
            it.children.remove(tag)
            tagParents.remove(id)
        }
    }
}