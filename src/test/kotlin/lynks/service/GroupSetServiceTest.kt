package lynks.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import lynks.common.exception.InvalidModelException
import lynks.group.*
import lynks.group.Collection
import lynks.util.TEST_USER
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

        every { tagService.getIn(TEST_USER, ids) } returns tags
        every { collectionService.getIn(TEST_USER, ids) } returns collections

        val groupSet = groupSetService.getIn(TEST_USER, ids)
        assertThat(groupSet.tags).isEqualTo(tags)
        assertThat(groupSet.collections).isEqualTo(collections)

        verify { tagService.getIn(TEST_USER, ids) }
        verify { collectionService.getIn(TEST_USER, ids) }
    }

    @Test
    fun testAssertGroupsSucceeds() {
        every { tagService.get(TEST_USER, "t1") } returns Tag("t1", "tag1", "tag1", Instant.EPOCH, Instant.EPOCH)
        every { tagService.get(TEST_USER, "t2") } returns Tag("t2", "tag2", "tag2", Instant.EPOCH, Instant.EPOCH)
        every { collectionService.get(TEST_USER, "c1") } returns Collection(
            "c1",
            "col1",
            "col1",
            mutableSetOf(),
            Instant.EPOCH,
            Instant.EPOCH
        )

        groupSetService.assertGroups(TEST_USER, listOf("t1", "t2"), listOf("c1"))
    }

    @Test
    fun testAssertGroupsFailsMissingTag() {
        every { tagService.get(TEST_USER, "t1") } returns Tag("t1", "tag1", "tag1", Instant.EPOCH, Instant.EPOCH)
        every { tagService.get(TEST_USER, "t2") } returns null
        every { collectionService.get(TEST_USER, "t2") } returns Collection(
            "c1",
            "col1",
            "col1",
            mutableSetOf(),
            Instant.EPOCH,
            Instant.EPOCH
        )

        assertThrows<InvalidModelException> {
            groupSetService.assertGroups(TEST_USER, listOf("t1", "t2"), listOf("c1"))
        }
    }

    @Test
    fun testAssertGroupsFailsMissingCollection() {
        every { tagService.get(TEST_USER, "t1") } returns Tag("t1", "tag1", "tag1", Instant.EPOCH, Instant.EPOCH)
        every { collectionService.get(TEST_USER, "c1") } returns null

        assertThrows<InvalidModelException> {
            groupSetService.assertGroups(TEST_USER, listOf("t1"), listOf("c1"))
        }
    }

    @Test
    fun testGetSubtrees() {
        val tags = listOf(
            Tag("t1", "tag1", "tag1", Instant.EPOCH, Instant.EPOCH),
            Tag("t2", "tag2", "tag1", Instant.EPOCH, Instant.EPOCH)
        )
        val collections = listOf(Collection("c1", "col1", "col1", mutableSetOf(), Instant.EPOCH, Instant.EPOCH))

        every { tagService.subtree(TEST_USER, "t1") } returns tags
        every { collectionService.subtree(TEST_USER, "c1") } returns collections

        val groupSet = groupSetService.subtrees(TEST_USER, listOf("t1"), listOf("c1"))
        assertThat(groupSet.tags).isEqualTo(tags)
        assertThat(groupSet.collections).isEqualTo(collections)

        verify { tagService.subtree(TEST_USER, "t1") }
        verify { collectionService.subtree(TEST_USER, "c1") }
    }

    @Test
    fun testGetSubtreesEmptyIds() {
        val subtrees = groupSetService.subtrees(TEST_USER, emptyList(), emptyList())
        assertThat(subtrees.tags).isEmpty()
        assertThat(subtrees.collections).isEmpty()

        verify(exactly = 0) { tagService.subtree(TEST_USER, any()) }
        verify(exactly = 0) { collectionService.subtree(TEST_USER, any()) }
    }

    @Test
    fun testMatchWithContent() {
        every { tagService.sequence(TEST_USER) } returns sequenceOf(
            Tag("t1", "tag1", "tag1", Instant.EPOCH, Instant.EPOCH),
            Tag("t2", "tag2", "tag2", Instant.EPOCH, Instant.EPOCH)
        )
        every { collectionService.sequence(TEST_USER) } returns sequenceOf(
            Collection("c1", "col1", "col1", mutableSetOf(), Instant.EPOCH, Instant.EPOCH),
            Collection("c2", "col2", "col2", mutableSetOf(), Instant.EPOCH, Instant.EPOCH)
        )

        val content = "some content tag1 along with tAG2 and col1 are relevant"
        val set = groupSetService.matchWithContent(TEST_USER, content)
        assertThat(set.tags).hasSize(2).extracting("id").containsExactly("t1", "t2")
        assertThat(set.collections).hasSize(1).extracting("id").containsExactly("c1")
    }

    @Test
    fun testMatchWithEmptyContent() {
        val set = groupSetService.matchWithContent(TEST_USER, null)
        assertThat(set.tags).isEmpty()
        assertThat(set.collections).isEmpty()
    }

}
