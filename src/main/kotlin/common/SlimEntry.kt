package common

import group.Collection
import group.Tag

interface SlimEntry : IdBasedCreatedEntity {
    val title: String
    val dateUpdated: Long
    val starred: Boolean
    val tags: List<Tag>
    val collections: List<Collection>
}