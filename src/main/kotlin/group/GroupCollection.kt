package group

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap

class GroupCollection<T: Grouping<T>> {

    // group : [children]
    private val groupTree: Multimap<T, T> = HashMultimap.create()

    // id : group
    private val groupLookup: MutableMap<String, T> = mutableMapOf()

    // id : parent
    private val groupParents: MutableMap<String, T> = mutableMapOf()

    // groups are root nodes
    fun build(groups: Collection<T>) {
        groupTree.clear()
        groupLookup.clear()
        groupParents.clear()
        groups.forEach { processGroup(it) }
    }

    private fun processGroup(group: T) {
        groupLookup[group.id] = group
        groupTree.putAll(group, traverseChildren(group, mutableSetOf()))
        for (child in group.children) {
            groupParents[child.id] = group
            processGroup(child)
        }
    }

    private fun traverseChildren(group: T, existing: MutableSet<T>): MutableSet<T> {
        existing.addAll(group.children)
        for(child in group.children) {
            existing.addAll(traverseChildren(child, existing))
        }
        return existing
    }

    private fun traverseParents(id: String, block: (T) -> Unit) {
        if(groupParents.containsKey(id)) {
            groupParents[id]?.also{
                block(groupParents[id]!!)
                traverseParents(it.id, block)
            }
        }
    }

    fun group(id: String): T? = groupLookup[id]

    fun groupsIn(ids: Collection<String>): List<T> = ids.mapNotNull { groupLookup[it] }

    fun add(group: T, parent: String?): T {
        groupLookup[group.id] = group
        parent?.also {
            val parentGroup = group(parent)!!
            groupTree.put(group, parentGroup)
            parentGroup.children.add(group)
            groupParents[group.id] = parentGroup
        }
        traverseParents(group.id) { groupTree.put(it, group) }
        return group
    }

    fun update(group: T, newParentId: String?): T {
        val currentParentId = groupParents[group.id]?.id
        if(currentParentId != newParentId) {
            // had a parent before so remove from children
            if(currentParentId != null) {
                val currentParent = group(currentParentId)!!
                currentParent.children.remove(group)
            }

            // add as child to new parent
            if(newParentId != null) {
                val newParent = group(newParentId)!!
                newParent.children.add(group)
            }

            // recursive update of parents
            build(all().toList())
        }
        val current = group(group.id)!!
        current.name = group.name
        current.children = group.children
        current.dateUpdated = group.dateUpdated
        current.dateCreated = group.dateCreated
        return current
    }

    fun subtree(id: String): MutableCollection<T> {
        val group = group(id)
        return groupTree[group].also { if(group != null) it.add(group) }
    }

    fun rootGroups(): Collection<T> = groupLookup.values.filter { !groupParents.containsKey(it.id) }

    fun all(): Collection<T> = groupLookup.values

    fun delete(id: String) {
        val group = groupLookup[id]
        groupTree.removeAll(id)
        groupLookup.remove(id)
        group?.children?.forEach{ delete(it.id) }
        traverseParents(id) { groupTree.remove(it, group) }
        groupParents[id]?.also {
            it.children.remove(group)
            groupParents.remove(id)
        }
    }
}
