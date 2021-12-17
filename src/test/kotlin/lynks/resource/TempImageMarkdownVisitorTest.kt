package lynks.resource

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import lynks.common.Environment
import lynks.common.IMAGE_UPLOAD_BASE
import lynks.common.TEMP_URL
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class TempImageMarkdownVisitorTest {

    private val eid = "eid"
    private val resourceManager = mockk<ResourceManager>()
    private val visitor = TempImageMarkdownVisitor(eid, resourceManager)

    private val groupInput = "${TEMP_URL}abc/one.png"
    private val fullInput = "![desc]($groupInput)"

    @Test
    fun testNoGroupFound() {
        val match = mockk<MatchResult>()
        val collection = mockk<MatchGroupCollection>()
        every { collection[any()] } returns null
        every { match.groups } returns collection
        val result = visitor.replace(match)
        assertThat(result).isNull()
        verify(exactly = 0) { resourceManager.migrateGeneratedResources(eid, any()) }
    }

    @Test
    fun testGroupsReplaced() {
        val match = setupMatchResultMock(fullInput, groupInput)
        every { resourceManager.constructTempBasePath(IMAGE_UPLOAD_BASE) } returns Path.of("migrated/")
        val resources = listOf(Resource("rid", "eid", "one", "png", ResourceType.UPLOAD, 12, 123L, 123L))
        every { resourceManager.migrateGeneratedResources(eid, any()) } returns resources
        val result = visitor.replace(match)
        assertThat(result).isEqualTo("![desc](${Environment.server.rootPath}/entry/$eid/resource/rid)")
        verify(exactly = 1) { resourceManager.migrateGeneratedResources(eid, any()) }
    }

    @Test
    fun testNoResourcesMigrated() {
        val match = setupMatchResultMock(fullInput, groupInput)
        every { resourceManager.constructTempBasePath(IMAGE_UPLOAD_BASE) } returns Path.of("migrated/")
        every { resourceManager.migrateGeneratedResources(eid, any()) } returns emptyList()
        val result = visitor.replace(match)
        assertThat(result).isNull()
        verify(exactly = 1) { resourceManager.migrateGeneratedResources(eid, any()) }
    }

    private fun setupMatchResultMock(full: String, group: String): MatchResult {
        val match = mockk<MatchResult>()
        val collection = mockk<MatchGroupCollection>()
        val matchGroup = mockk<MatchGroup>()
        every { match.groups } returns collection
        every { collection[any()] } returns matchGroup
        every { matchGroup.value } returns group
        every { match.value } returns full
        return match
    }

}
