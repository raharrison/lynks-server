package service

import model.*
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import util.RandomUtils
import util.RowMapper.toTag

class TagService {

    private val tagCollection by lazy {
        TagCollection().apply { build(queryAllTags()) }
    }

    private fun getTagChildren(id: String): MutableSet<Tag> = transaction {
        Tags.select { Tags.parentId eq id }.map { toTag(it, ::getTagChildren) }.toMutableSet()
    }

    private fun queryTag(id: String): Tag? = transaction {
        Tags.select { Tags.id eq id}
                .map { toTag(it, ::getTagChildren) }.singleOrNull()
    }

    private fun queryAllTags(): List<Tag> = transaction {
        Tags.select { Tags.parentId.isNull() }
                .map { toTag(it, ::getTagChildren) }
    }

    // TODO: only return root nodes
    fun getAllTags(): Collection<Tag> = tagCollection.all()

    fun getTags(ids: List<String>): List<Tag> = tagCollection.tagsIn(ids)

    fun getTag(id: String): Tag? = tagCollection.tag(id)

    fun subtree(id: String): Collection<Tag> = tagCollection.subtree(id)

    fun updateTag(tag: NewTag): Tag {
        val id = tag.id
        return if (id == null) {
            addTag(tag)
        } else {
            transaction {
                val currentParentId: String? = Tags.slice(Tags.parentId).select{ Tags.id eq id}
                        .single()[Tags.parentId]
                Tags.update({ Tags.id eq id }) {
                    it[name] = tag.name
                    it[parentId] = tag.parentId
                    it[dateUpdated] = System.currentTimeMillis()
                }
                tagCollection.update(currentParentId, tag.parentId, queryTag(id)!!)
                //rebuild()
                //getTag(id)!!
            }
        }
    }

    fun addTag(tag: NewTag): Tag = transaction {
        val newId = RandomUtils.generateUid()
        Tags.insert({
            it[id] = newId
            it[name] = tag.name
            it[parentId] = tag.parentId
            it[dateUpdated] = System.currentTimeMillis()
        })
            val created =  queryTag(newId)!!
        tagCollection.add(created, tag.parentId)
    }

    fun deleteTag(id: String): Boolean = transaction {
        EntryTags.deleteWhere { EntryTags.tagId eq id }
        // delete children
        Tags.select { Tags.parentId eq id }.forEach { deleteTag(it[Tags.id]) }
        Tags.deleteWhere { Tags.id eq id }.also { tagCollection.delete(id) } > 0
    }
}
