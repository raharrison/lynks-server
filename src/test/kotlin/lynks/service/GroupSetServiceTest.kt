package lynks.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import lynks.common.exception.InvalidModelException
import lynks.group.*
import lynks.group.Collection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class GroupSetServiceTest {

    private val tagService = mockk<TagService>()
    private val collectionService = mockk<CollectionService>()
    private val groupSetService = GroupSetService(tagService, collectionService)

    @Test
    fun testGetGroupsIn() {
        val ids = listOf("t1", "t2", "c1")
        val tags = listOf(
            Tag("t1", "tag1", "tag1", Instant.EPOCH, Instant.EPOCH),
            Tag("t2", "tag2", "tag2", Instant.EPOCH, Instant.EPOCH)
        )
        val collections = listOf(Collection("c1", "col1", "col1", mutableSetOf(), Instant.EPOCH, Instant.EPOCH))

        every { tagService.getIn(ids) } returns tags
        every { collectionService.getIn(ids) } returns collections

        val groupSet = groupSetService.getIn(ids)
        assertThat(groupSet.tags).isEqualTo(tags)
        assertThat(groupSet.collections).isEqualTo(collections)

        verify { tagService.getIn(ids) }
        verify { collectionService.getIn(ids) }
    }

    @Test
    fun testAssertGroupsSucceeds() {
        every { tagService.get("t1") } returns Tag("t1", "tag1", "tag1", Instant.EPOCH, Instant.EPOCH)
        every { tagService.get("t2") } returns Tag("t2", "tag2", "tag2", Instant.EPOCH, Instant.EPOCH)
        every { collectionService.get("c1") } returns Collection("c1", "col1", "col1", mutableSetOf(), Instant.EPOCH, Instant.EPOCH)

        groupSetService.assertGroups(listOf("t1", "t2"), listOf("c1"))
    }

    @Test
    fun testAssertGroupsFailsMissingTag() {
        every { tagService.get("t1") } returns Tag("t1", "tag1", "tag1", Instant.EPOCH, Instant.EPOCH)
        every { tagService.get("t2") } returns null
        every { collectionService.get("t2") } returns Collection("c1", "col1", "col1", mutableSetOf(), Instant.EPOCH, Instant.EPOCH)

        assertThrows<InvalidModelException> {
            groupSetService.assertGroups(listOf("t1", "t2"), listOf("c1"))
        }
    }

    @Test
    fun testAssertGroupsFailsMissingCollection() {
        every { tagService.get("t1") } returns Tag("t1", "tag1", "tag1", Instant.EPOCH, Instant.EPOCH)
        every { collectionService.get("c1") } returns null

        assertThrows<InvalidModelException> {
            groupSetService.assertGroups(listOf("t1"), listOf("c1"))
        }
    }

    @Test
    fun testGetSubtrees() {
        val tags = listOf(
            Tag("t1", "tag1", "tag1", Instant.EPOCH, Instant.EPOCH),
            Tag("t2", "tag2", "tag1", Instant.EPOCH, Instant.EPOCH)
        )
        val collections = listOf(Collection("c1", "col1", "col1", mutableSetOf(), Instant.EPOCH, Instant.EPOCH))

        every { tagService.subtree("t1") } returns tags
        every { collectionService.subtree("c1") } returns collections

        val groupSet = groupSetService.subtrees(listOf("t1"), listOf("c1"))
        assertThat(groupSet.tags).isEqualTo(tags)
        assertThat(groupSet.collections).isEqualTo(collections)

        verify { tagService.subtree("t1") }
        verify { collectionService.subtree("c1") }
    }

    @Test
    fun testGetSubtreesEmptyIds() {
        val subtrees = groupSetService.subtrees(emptyList(), emptyList())
        assertThat(subtrees.tags).isEmpty()
        assertThat(subtrees.collections).isEmpty()

        verify(exactly = 0) { tagService.subtree(any()) }
        verify(exactly = 0) { collectionService.subtree(any()) }
    }

    @Test
    fun testMatchWithContent() {
        every { tagService.sequence() } returns sequenceOf(
            Tag("t1", "tag1", "tag1", Instant.EPOCH, Instant.EPOCH),
            Tag("t2", "tag2", "tag2", Instant.EPOCH, Instant.EPOCH)
        )
        every { collectionService.sequence() } returns sequenceOf(
            Collection("c1", "col1", "col1", mutableSetOf(), Instant.EPOCH, Instant.EPOCH),
            Collection("c2", "col2", "col2", mutableSetOf(), Instant.EPOCH, Instant.EPOCH)
        )

        val content = "some content tag1 along with tAG2 and col1 are relevant"
        val set = groupSetService.matchWithContent(content)
        assertThat(set.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")
        assertThat(set.collections).hasSize(1).extracting("id").containsExactly("c1")
    }

    @Test
    fun testMatchWithEmptyContent() {
        val set = groupSetService.matchWithContent(null)
        assertThat(set.tags).isEmpty()
        assertThat(set.collections).isEmpty()
    }

}
