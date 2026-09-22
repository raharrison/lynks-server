package lynks.service

import io.mockk.every
import io.mockk.mockk
import lynks.common.*
import lynks.entry.EntryAuditService
import lynks.entry.NoteService
import lynks.group.CollectionService
import lynks.group.GroupSetService
import lynks.group.TagService
import lynks.resource.*
import lynks.util.markdown.MarkdownProcessor
import lynks.worker.WorkerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class NoteTempImageTest : DatabaseTest() {

    private val resourceManager = ResourceManager(FileStore(), ResourceRepository())
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private val noteService = NoteService(
        GroupSetService(TagService(), CollectionService()),
        entryAuditService,
        resourceManager,
        mockk<WorkerRegistry>(relaxUnitFun = true),
        MarkdownProcessor(resourceManager, mockk())
    )

    @AfterEach
    fun cleanUp() {
        Paths.get(Environment.resource.resourceBasePath).toFile().deleteRecursively()
    }

    @Test
    fun testCreateNoteMovesPastedImage() {
        val upload = upload(byteArrayOf(1, 2, 3))

        val note = noteService.add(newNote("before ![img](${url(upload)}) after"))

        val resource = resourceManager.getResourcesFor(note.id).single()
        assertThat(resource.type).isEqualTo(ResourceType.UPLOAD)
        assertThat(resource.size).isEqualTo(3)
        assertThat(note.plainContent)
            .contains("${Environment.server.rootPath}/entry/${note.id}/resource/${resource.id}")
            .doesNotContain(TEMP_URL)
        assertThat(Files.exists(upload)).isFalse()
        assertThat(resourceManager.getResourceAsFile(resource.id)!!.second.readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun testCreateNoteStoresRewrittenContentAsFirstVersion() {
        val note = noteService.add(newNote("![img](${url(upload(byteArrayOf(1)))})"))

        val firstVersion = noteService.get(note.id, 1)

        assertThat(note.version).isOne()
        assertThat(firstVersion?.plainContent).isEqualTo(note.plainContent)
    }

    @Test
    fun testCreateNoteWithTwoImagesKeepsBoth() {
        val content = "![a](${url(upload(byteArrayOf(1)))}) ![b](${url(upload(byteArrayOf(2, 2)))})"

        val note = noteService.add(newNote(content))

        assertThat(resourceManager.getResourcesFor(note.id)).hasSize(2)
        assertThat(note.plainContent).doesNotContain(TEMP_URL)
    }

    @Test
    fun testCreateNoteReferencingSameImageTwice() {
        val url = url(upload(byteArrayOf(1)))

        val note = noteService.add(newNote("![a]($url) and again ![b]($url)"))

        val resource = resourceManager.getResourcesFor(note.id).single()
        assertThat(note.plainContent.split("/resource/${resource.id}")).hasSize(3)
    }

    @Test
    fun testUpdateNoteMovesPastedImage() {
        val added = noteService.add(newNote("nothing yet"))
        val upload = upload(byteArrayOf(1))

        val updated = noteService.update(NewNote(added.id, "n1", "![img](${url(upload)})", emptyList(), emptyList()))

        val resource = resourceManager.getResourcesFor(added.id).single()
        assertThat(updated?.plainContent).contains("/resource/${resource.id}")
        assertThat(Files.exists(upload)).isFalse()
    }

    @Test
    fun testFailedCreateLeavesPastedImageForRetry() {
        val upload = upload(byteArrayOf(1))
        every { entryAuditService.acceptAuditEvent(any(), any(), any()) } throws IllegalStateException("audit down")

        assertThrows<IllegalStateException> { noteService.add(newNote("![img](${url(upload)})")) }

        assertThat(Files.exists(upload)).isTrue()
        assertThat(noteService.get().content).isEmpty()
    }

    @Test
    fun testUnknownTempImageIsLeftAlone() {
        val content = "![img](${TEMP_UPLOAD_URL}missing.png)"

        val note = noteService.add(newNote(content))

        assertThat(note.plainContent).isEqualTo(content)
        assertThat(resourceManager.getResourcesFor(note.id)).isEmpty()
    }

    private fun upload(data: ByteArray): Path = resourceManager.saveTempUpload(data, PNG)

    private fun url(upload: Path) = "$TEMP_UPLOAD_URL${upload.fileName}"

    private fun newNote(content: String) = NewNote(null, "n1", content, emptyList(), emptyList())

}
