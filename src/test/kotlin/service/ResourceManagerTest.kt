package service

import common.DatabaseTest
import common.EntryType
import common.Environment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import resource.*
import util.FileUtils.getExtension
import util.FileUtils.removeExtension
import util.createDummyEntry
import util.toUrlString
import java.nio.file.Files
import java.nio.file.Paths

class ResourceManagerTest: DatabaseTest() {

    private val resourceManager = ResourceManager()

    @AfterEach
    fun cleanUp() {
        Paths.get(Environment.server.resourceBasePath).toFile().deleteRecursively()
    }

    @BeforeEach
    fun createEntries() {
        createDummyEntry("eid", "note", "content", EntryType.NOTE)
        Thread.sleep(10)
        createDummyEntry("eid2", "link", "website", EntryType.LINK)
    }

    @Test
    fun testGetResourcesNoEntry() {
        assertThat(resourceManager.getResourcesFor("nothing")).isEmpty()
    }

    @Test
    fun testGetResourceDoesntExist() {
        assertThat(resourceManager.getResource("nothing")).isNull()
    }

    @Test
    fun testGetResourceAsFileDoesntExist() {
        assertThat(resourceManager.getResourceAsFile("nothing")).isNull()
    }

    @Test
    fun testCreateTempFile() {
        val src = "google.com"
        // create document
        val doc = byteArrayOf(1,2,3,4,5)
        val docPath = resourceManager.saveTempFile(src, doc, ResourceType.DOCUMENT, HTML)
                .let { tempFileToFullPath(it).toUrlString() }
        assertThat(fileExists(docPath)).isTrue()
        assertFileContents(docPath, doc)

        assertThat(docPath).startsWith(Environment.server.resourceTempPath)
        val docFile = fileName(docPath)
        assertThat(getExtension(docFile)).isEqualTo(HTML)
        assertThat(removeExtension(docFile)).isEqualTo(ResourceType.DOCUMENT.toString().toLowerCase())

        // create thumbnail
        val thumb = byteArrayOf(6,7,8,9,10)
        val thumbPath = resourceManager.saveTempFile(src, thumb, ResourceType.THUMBNAIL, JPG)
                .let { tempFileToFullPath(it).toUrlString() }
        assertThat(fileExists(thumbPath)).isTrue()
        assertFileContents(thumbPath, thumb)

        assertThat(thumbPath).startsWith(Environment.server.resourceTempPath)
        val thumbFile = fileName(thumbPath)
        assertThat(getExtension(thumbFile)).isEqualTo(JPG)
        assertThat(removeExtension(thumbFile)).isEqualTo(ResourceType.THUMBNAIL.toString().toLowerCase())

        // parent dir contains 2 files
        val folder = Paths.get(thumbPath).parent
        assertFileCount(folder.toString(), 2)
    }

    @Test
    fun testTempFileDirUniqueToSource() {
        val data1 = byteArrayOf(1,2,3,4,5)
        val data2 = byteArrayOf(6,7,8,9,10)
        val path1 = resourceManager.saveTempFile("imgur.com", data1, ResourceType.DOCUMENT, HTML)
                .let { tempFileToFullPath(it).toUrlString() }
        val path2 = resourceManager.saveTempFile("gmail.com", data2, ResourceType.THUMBNAIL, JPG)
                .let { tempFileToFullPath(it).toUrlString() }
        assertFileCount(Environment.server.resourceTempPath, 2)
        assertFileCount(Paths.get(path1).parent.toString(), 1)
        assertFileCount(Paths.get(path2).parent.toString(), 1)
    }

    @Test
    fun testMoveTempFiles() {
        val data1 = byteArrayOf(1,2,3,4,5)
        val data2 = byteArrayOf(6,7,8,9,10)
        val path1 = resourceManager.saveTempFile("youtube.com", data1, ResourceType.DOCUMENT, HTML)
                .let { tempFileToFullPath(it).toUrlString() }
        val path2 = resourceManager.saveTempFile("youtube.com", data2, ResourceType.THUMBNAIL, JPG)
                .let { tempFileToFullPath(it).toUrlString() }
        val path3 = resourceManager.saveTempFile("twitter.com", data2, ResourceType.THUMBNAIL, JPG)
                .let { tempFileToFullPath(it).toUrlString() }

        assertThat(resourceManager.moveTempFiles("eid", "youtube.com")).isTrue()

        // check resources generated
        val resources = resourceManager.getResourcesFor("eid")
        assertThat(resources).hasSize(2)
        for (resource in resources) {
            assertThat(resource.entryId).isEqualTo("eid")
            assertThat(resource.dateUpdated).isEqualTo(resource.dateCreated)
            assertThat(resource.name).isEqualTo("${resource.type.name.toLowerCase()}.${resource.extension}")
            when(resource.type) {
                ResourceType.DOCUMENT -> {
                    assertThat(resource.extension).isEqualTo(HTML)
                    assertThat(resource.size).isEqualTo(data1.size.toLong())
                    assertFileContents(resourceManager.constructPath("eid", "${resource.id}.${resource.extension}").toString(), data1)
                }
                ResourceType.THUMBNAIL -> {
                    assertThat(resource.extension).isEqualTo(JPG)
                    assertThat(resource.size).isEqualTo(data2.size.toLong())
                    assertFileContents(resourceManager.constructPath("eid", "${resource.id}.${resource.extension}").toString(), data2)
                }
                else -> fail("wrong type")
            }
        }

        // check files moved to main area
        assertFileCount(resourceManager.constructPath("eid", "").toString(), 2)

        // check temp files
        assertThat(fileExists(path1)).isFalse()
        assertThat(fileExists(path2)).isFalse()
        assertThat(fileExists(path3)).isTrue()
    }

    @Test
    fun testMoveTempFileDoesntExist() {
        assertThat(resourceManager.moveTempFiles("eid", "nothing")).isFalse()
    }

    @Test
    fun testConstructPath() {
        val eid = "id1"
        val file = "file.txt"
        val path = resourceManager.constructPath(eid, file)
        val pathStr = path.toUrlString()
        assertThat(pathStr).startsWith(Environment.server.resourceBasePath)
        assertThat(path.fileName.toString()).isEqualTo(file)
        assertThat(path.parent.fileName.toString()).isEqualTo(eid)
    }

    @Test
    fun testSaveGeneratedResourceNoFile() {
        val filename = "file.txt"
        val extension = "txt"
        val length = 127L
        val resource = resourceManager.saveGeneratedResource(
                entryId = "eid",
                name = filename,
                extension = extension,
                size = length,
                type = ResourceType.UPLOAD)
        assertThat(resource.entryId).isEqualTo("eid")
        assertThat(resource.name).isEqualTo(filename)
        assertThat(resource.extension).isEqualTo(extension)
        assertThat(resource.size).isEqualTo(length)
        assertThat(resource.type).isEqualTo(ResourceType.UPLOAD)
        assertThat(resource.dateCreated).isEqualTo(resource.dateUpdated)

        val retrieved = resourceManager.getResource(resource.id)
        assertThat(retrieved).isEqualTo(resource)
    }

    @Test
    fun testGetResourceById() {
        val resource1 = resourceManager.saveGeneratedResource("rid", "eid", "file1.txt", "txt", ResourceType.UPLOAD, 12L)
        val resource2 = resourceManager.saveGeneratedResource("rid2", "eid2", "file2.html", HTML, ResourceType.SCREENSHOT, 15L)

        val retrieved1 = resourceManager.getResource("rid")
        assertThat(retrieved1).isNotNull.isEqualTo(resource1)
        assertThat(retrieved1).isEqualTo(Resource("rid", "eid", "file1.txt", "txt", ResourceType.UPLOAD, 12L, resource1.dateCreated, resource1.dateUpdated))

        val retrieved2 = resourceManager.getResource("rid2")
        assertThat(retrieved2).isNotNull.isEqualTo(resource2)
        assertThat(retrieved2).isEqualTo(Resource("rid2", "eid2", "file2.html", HTML, ResourceType.SCREENSHOT, 15L, resource2.dateCreated, resource2.dateUpdated))
    }

    @Test
    fun testGetResourcesForEntry() {
        val resource1 = resourceManager.saveGeneratedResource("rid", "eid", "file1.txt", "txt", ResourceType.UPLOAD, 12L)
        val resource2 = resourceManager.saveGeneratedResource("rid2", "eid2", "file2.html", HTML, ResourceType.SCREENSHOT, 15L)
        val resource3 = resourceManager.saveGeneratedResource("rid3", "eid2", "file3.kt", "kt", ResourceType.DOCUMENT, 22L)

        val e1 = resourceManager.getResourcesFor("eid")
        assertThat(e1).hasSize(1).containsExactly(resource1)

        val e2 = resourceManager.getResourcesFor("eid2")
        assertThat(e2).hasSize(2).containsExactlyInAnyOrder(resource2, resource3)
    }

    @Test
    fun testSaveGeneratedResourceWithData() {
        val entryId = "eid"
        val extension = JPG
        val data = byteArrayOf(1,2,3,4,5)
        val resource = resourceManager.saveGeneratedResource(entryId, "res1.jpg", ResourceType.SCREENSHOT, data)
        assertThat(resource.entryId).isEqualTo(entryId)
        assertThat(resource.extension).isEqualTo(extension)
        assertThat(resource.size).isEqualTo(data.size.toLong())
        assertThat(resource.dateUpdated).isEqualTo(resource.dateCreated)
        assertThat(resource.type).isEqualTo(ResourceType.SCREENSHOT)
        assertThat(resource.name).isEqualTo("res1.jpg")

        assertFileContents(resourceManager.constructPath(entryId, "${resource.id}.${resource.extension}").toString(), data)
        resourceManager.saveGeneratedResource(entryId, "res2.jpg", ResourceType.THUMBNAIL, data)
        assertFileCount(resourceManager.constructPath(entryId, "").toString(), 2)
        assertFileContents(resourceManager.constructPath(entryId, "${resource.id}.${resource.extension}").toString(), data)

        val res = resourceManager.getResourceAsFile(resource.id)
        assertThat(res?.first).isEqualTo(resource)
        assertThat(res?.second?.readBytes()).isEqualTo(data)
        assertThat(res?.second?.name).isEqualTo("${resource.id}.$extension")
        assertThat(resourceManager.getResource(resource.id)).isEqualTo(resource)
        assertThat(res?.second?.toPath()?.toUrlString()).startsWith(Environment.server.resourceBasePath)
    }

    @Test
    fun testSaveGeneratedResourceFromFile() {
        val entryId = "eid"
        val data = byteArrayOf(1,2,3,4,5)
        val path = resourceManager.constructPath(entryId, "res1.jpg")
        path.toFile().apply {
            parentFile.mkdirs()
            createNewFile()
        }
        Files.write(path, data)
        assertFileCount(resourceManager.constructPath(entryId, "").toString(), 1)
        assertFileContents(path.toString(), data)

        val resource = resourceManager.saveGeneratedResource("eid", ResourceType.GENERATED, path)
        assertThat(resource.entryId).isEqualTo(entryId)
        assertThat(resource.extension).isEqualTo(JPG)
        assertThat(resource.size).isEqualTo(data.size.toLong())
        assertThat(resource.dateUpdated).isEqualTo(resource.dateCreated)
        assertThat(resource.type).isEqualTo(ResourceType.GENERATED)
        assertThat(resource.name).isEqualTo("res1.jpg")

        assertThat(path.toFile().exists()).isFalse()
        val newPath = resourceManager.constructPath(entryId, "${resource.id}.${resource.extension}")
        assertThat(newPath.toFile().exists()).isTrue()
        assertFileCount(resourceManager.constructPath(entryId, "").toString(), 1)
        assertFileContents(newPath.toString(), data)
    }

    @Test
    fun testGetResourceAsFile() {
        val data = byteArrayOf(1,2,3,4,5)
        val resource = resourceManager.saveGeneratedResource("eid", "res.jpg", ResourceType.SCREENSHOT, data)
        val res = resourceManager.getResourceAsFile(resource.id)
        assertThat(res?.second?.readBytes()).isEqualTo(data)
        assertThat(res?.second?.name).isEqualTo("${resource.id}.$JPG")
    }

    @Test
    fun testSaveUploadedResource() {
        val entryId = "eid"
        val name = "content.txt"
        val data = byteArrayOf(1,2,3)
        val resource = resourceManager.saveUploadedResource(entryId, name, data.inputStream())
        assertThat(resource.entryId).isEqualTo(entryId)
        assertThat(resource.extension).isEqualTo("txt")
        assertThat(resource.size).isEqualTo(data.size.toLong())
        assertThat(resource.dateUpdated).isEqualTo(resource.dateCreated)
        assertThat(resource.type).isEqualTo(ResourceType.UPLOAD)
        assertThat(resource.name).isEqualTo(name)

        assertThat(resourceManager.getResource(resource.id)).isEqualTo(resource)

        val res = resourceManager.getResourceAsFile(resource.id)
        assertThat(res?.first).isEqualTo(resource)
        assertThat(res?.second?.readBytes()).isEqualTo(data)
        assertThat(res?.second?.name).isEqualTo("${resource.id}.${resource.extension}")
        assertThat(res?.second?.toPath()?.toUrlString()).startsWith(Environment.server.resourceBasePath)
    }

    @Test
    fun testUpdateResource() {
        val entryId = "eid"
        val name = "content.txt"
        val data = byteArrayOf(1,2,3,4,5)
        val resource = resourceManager.saveUploadedResource(entryId, name, data.inputStream())
        assertThat(resourceManager.getResource(resource.id)).isNotNull
        assertThat(resource.name).isEqualTo("content.txt")
        assertThat(resource.extension).isEqualTo("txt")
        assertFileCount(resourceManager.constructPath(entryId, "").toString(), 1)
        val originalResourceAsFile = resourceManager.getResourceAsFile(resource.id)
        assertThat(originalResourceAsFile?.second?.name).endsWith("${resource.id}.txt")
        assertFileContents(originalResourceAsFile?.second.toString(), data)

        val updateResourceRequest = resource.copy(name="updated.xml")
        Thread.sleep(5)
        val updatedResource = resourceManager.updateResource(updateResourceRequest)
        assertThat(updatedResource).isNotNull()
        assertThat(updatedResource?.id).isEqualTo(resource.id)
        assertThat(updatedResource?.entryId).isEqualTo(entryId)
        assertThat(updatedResource?.name).isEqualTo("updated.xml")
        assertThat(updatedResource?.extension).isEqualTo("xml")
        assertThat(updatedResource?.size).isEqualTo(data.size.toLong())
        assertThat(updatedResource?.dateUpdated).isNotEqualTo(updatedResource?.dateCreated)
        assertThat(updatedResource?.type).isEqualTo(ResourceType.UPLOAD)

        val retrievedResource = resourceManager.getResource(resource.id)
        assertThat(retrievedResource).isEqualTo(updatedResource)
        assertFileCount(resourceManager.constructPath(entryId, "").toString(), 1)

        val resourceAsFile = resourceManager.getResourceAsFile(resource.id)
        assertThat(resourceAsFile?.second?.name).endsWith("${resource.id}.xml")
        assertThat(resourceAsFile?.second?.exists()).isTrue()
        assertFileContents(resourceAsFile?.second.toString(), data)
        assertThat(originalResourceAsFile?.second?.exists()).isFalse()
    }

    @Test
    fun testUpdateResourceDoesntExist() {
        val resource = Resource("invalid", "eid", "file1.txt", "txt", ResourceType.UPLOAD, 12L, 1234, 12345)
        val updated = resourceManager.updateResource(resource)
        assertThat(updated).isNull()
    }

    @Test
    fun testDeleteResourceDoesntExist() {
        assertThat(resourceManager.delete("nothing")).isFalse()
    }

    @Test
    fun testDeleteAllForEntryDoesntExist() {
        // still successfully deleted everything
        assertThat(resourceManager.deleteAll("nothing")).isTrue()
    }

    @Test
    fun testDeleteResource() {
        val entryId = "eid"
        val data = byteArrayOf(1,2,3,4,5)
        val resource = resourceManager.saveGeneratedResource(entryId, "res.jpg", ResourceType.SCREENSHOT, data)
        assertThat(resourceManager.getResource(resource.id)).isNotNull
        assertFileCount(resourceManager.constructPath(entryId, "").toString(), 1)
        assertThat(resourceManager.delete(resource.id)).isTrue()
        assertThat(resourceManager.getResource(resource.id)).isNull()
        assertFileCount(resourceManager.constructPath(entryId, "").toString(), 0)
    }

    @Test
    fun testDeleteEntry() {
        val entryId = "eid"
        val data = byteArrayOf(1,2,3,4,5)
        val data2 = byteArrayOf(5,6,7,8,9)
        val resource = resourceManager.saveGeneratedResource(entryId, "res1.jpg", ResourceType.THUMBNAIL, data)
        val resource2 = resourceManager.saveGeneratedResource(entryId, "res2.png", ResourceType.SCREENSHOT, data2)

        assertThat(resourceManager.getResource(resource.id)).isNotNull
        assertThat(resourceManager.getResource(resource2.id)).isNotNull

        assertFileCount(resourceManager.constructPath(entryId, "").toString(), 2)
        assertThat(resourceManager.deleteAll(entryId)).isTrue()
        assertThat(resourceManager.getResource(resource.id)).isNull()
        assertThat(resourceManager.getResource(resource2.id)).isNull()
        assertThat(Files.exists(resourceManager.constructPath(entryId, ""))).isFalse()
    }

    private fun fileExists(path: String) = Files.exists(Paths.get(path))

    private fun assertFileContents(path: String, data: ByteArray) {
        val content = Files.readAllBytes(Paths.get(path))
        assertThat(content).isEqualTo(data)
    }

    private fun assertFileCount(path: String, count: Long) {
        Files.list(Paths.get(path)).use {
            assertThat(it.count()).isEqualTo(count)
        }
    }

    private fun fileName(path: String) = Paths.get(path).fileName.toString()

    private fun tempFileToFullPath(path: String) = Paths.get(Environment.server.resourceTempPath).resolve(Paths.get(path))

}