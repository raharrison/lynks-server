package group

import kotlin.collections.Collection

class GroupCollection<T: Grouping<T>> {

    // group : [children]
    private val groupTree: MutableMap<T, MutableSet<T>> = mutableMapOf()

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
        groupTree.getOrPut(group) { mutableSetOf() }.addAll(traverseChildren(group, mutableSetOf()))
        group.path = generatePath(group)
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
        if (groupParents.containsKey(id)) {
            groupParents[id]?.also {
                block(groupParents[id]!!)
                traverseParents(it.id, block)
            }
        }
    }

    private fun generatePath(group: T): String {
        return groupParents[group.id]?.let {
            if (it.path == null) {
                it.path = generatePath(it)
            }
            it.path + "/" + group.name
        } ?: group.name
    }

    fun group(id: String): T? = groupLookup[id]

    fun groupsIn(ids: Collection<String>): List<T> = ids.mapNotNull { groupLookup[it] }

    fun add(group: T, parent: String?): T {
        groupLookup[group.id] = group
        parent?.also {
            val parentGroup = group(parent)!!
            parentGroup.children.add(group)
            groupParents[group.id] = parentGroup
        }
        traverseParents(group.id) { groupTree.getOrPut(it) { mutableSetOf() }.add(group) }
        group.path = generatePath(group)
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
        current.path = generatePath(current)
        current.children = group.children
        current.dateUpdated = group.dateUpdated
        current.dateCreated = group.dateCreated
        return current
    }

    fun subtree(id: String): MutableCollection<T> {
        return group(id)?.let { group ->
            return groupTree.getValue(group).toMutableSet().also { it.add(group) }
        } ?: mutableSetOf()
    }

    fun rootGroups(): Collection<T> = groupLookup.values.filter { !groupParents.containsKey(it.id) }

    fun all(): Collection<T> = groupLookup.values

    fun delete(id: String) {
        val group = groupLookup[id]
        groupTree.remove(group)
        groupLookup.remove(id)
        group?.children?.forEach{ delete(it.id) }
        traverseParents(id) { groupTree[it]?.remove(group) }
        groupParents[id]?.also {
            it.children.remove(group)
            groupParents.remove(id)
        }
    }
}
