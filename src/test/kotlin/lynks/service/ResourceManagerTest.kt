package lynks.service

import lynks.common.*
import lynks.resource.*
import lynks.util.FileUtils.getExtension
import lynks.util.FileUtils.removeExtension
import lynks.util.createDummyEntry
import lynks.util.toUrlString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.nio.file.Files
import java.nio.file.Paths

class ResourceManagerTest: DatabaseTest() {

    private val resourceManager = ResourceManager()

    @AfterEach
    fun cleanUp() {
        Paths.get(Environment.resource.resourceBasePath).toFile().deleteRecursively()
    }

    @BeforeEach
    fun createEntries() {
        createDummyEntry("eid", "note", "content", EntryType.NOTE)
        Thread.sleep(10)
        createDummyEntry("eid2", "link", "website", EntryType.LINK)
    }

    @Test
    fun testGetResourcesNoEntry() {
        assertThat(resourceManager.getResourcesFor(EntryId("nothing"))).isEmpty()
    }

    @Test
    fun testGetResourceDoesntExist() {
        assertThat(resourceManager.getResource(ResourceId("nothing"))).isNull()
    }

    @Test
    fun testGetResourceAsFileDoesntExist() {
        assertThat(resourceManager.getResourceAsFile(ResourceId("nothing"))).isNull()
    }

    @Test
    fun testSaveTempFile() {
        val src = "google.com"
        // create document
        val doc = byteArrayOf(1,2,3,4,5)
        val docPath = resourceManager.saveTempFile(src, doc, ResourceType.DOCUMENT, HTML)
                .let { tempFileToFullPath(it).toUrlString() }
        assertThat(fileExists(docPath)).isTrue()
        assertFileContents(docPath, doc)

        assertThat(docPath).contains(Environment.resource.resourceTempPath)
        val docFile = fileName(docPath)
        assertThat(getExtension(docFile)).isEqualTo(HTML)
        assertThat(removeExtension(docFile)).startsWith(ResourceType.DOCUMENT.toString().lowercase())

        // create thumbnail
        val thumb = byteArrayOf(6,7,8,9,10)
        val thumbPath = resourceManager.saveTempFile(src, thumb, ResourceType.THUMBNAIL, JPG)
                .let { tempFileToFullPath(it).toUrlString() }
        assertThat(fileExists(thumbPath)).isTrue()
        assertFileContents(thumbPath, thumb)

        assertThat(thumbPath).contains(Environment.resource.resourceTempPath)
        val thumbFile = fileName(thumbPath)
        assertThat(getExtension(thumbFile)).isEqualTo(JPG)
        assertThat(removeExtension(thumbFile)).startsWith(ResourceType.THUMBNAIL.toString().lowercase())

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
        assertFileCount(Environment.resource.resourceTempPath, 2)
        assertFileCount(Paths.get(path1).parent.toString(), 1)
        assertFileCount(Paths.get(path2).parent.toString(), 1)
    }

    @Test
    fun testCreateTempFile() {
        val src = "src"
        val extension = "html"
        val tempFile = resourceManager.createTempFile(src, extension)
        assertThat(tempFile.src).isEqualTo(src)
        assertThat(tempFile.extension).isEqualTo(extension)
        val path = tempFile.path
        tempFile.use {
            assertThat(path.fileName).isNotEqualTo(src)
            assertThat(getExtension(path.fileName.toString())).isEqualTo(extension)
            assertThat(Files.exists(path)).isFalse()
            Files.writeString(path, "test content")
            assertThat(Files.exists(path)).isTrue()
        }
        assertThat(Files.exists(path)).isFalse()
    }

    @Test
    fun testCreateTempFileThrowsWhenClosed() {
        val tempFile = resourceManager.createTempFile("src", "html")
        tempFile.use {
            assertThat(Files.exists(tempFile.path)).isFalse()
        }
        assertThrows<IllegalStateException> {
            assertThat(Files.exists(tempFile.path)).isFalse()
        }
    }

    @Test
    fun testMoveTempFiles() {
        val data1 = byteArrayOf(1,2,3,4,5)
        val data2 = byteArrayOf(6,7,8,9,10)
        val path1 = resourceManager.saveTempFile("youtube.com", data1, ResourceType.DOCUMENT, HTML)
                .let { tempFileToFullPath(it).toUrlString() }
        val path2 = resourceManager.saveTempFile("twitter.com", data2, ResourceType.THUMBNAIL, JPG)
                .let { tempFileToFullPath(it).toUrlString() }

        val generatedResources = listOf(
            GeneratedResource(ResourceType.DOCUMENT, path1, HTML),
            GeneratedResource(ResourceType.THUMBNAIL, path2, JPG),
        )
        val migratedResources = resourceManager.migrateGeneratedResources(EntryId("eid"), generatedResources)
        assertThat(migratedResources).hasSize(2)
        assertThat(migratedResources).extracting<ResourceId> { it.id }
            .doesNotHaveDuplicates()
        assertThat(migratedResources).extracting("parentId").doesNotHaveDuplicates()
        assertThat(migratedResources).extracting("version").containsOnly(1)
        assertThat(migratedResources).extracting<EntryId> { it.entryId }
            .containsOnly(EntryId("eid"))
        assertThat(migratedResources).extracting("extension").containsOnly(HTML, JPG)
        assertThat(migratedResources).extracting("extension").containsOnly(HTML, JPG)

        // check resources generated
        val resources = resourceManager.getResourcesFor(EntryId("eid"))
        assertThat(resources).hasSize(2)
        for (resource in resources) {
            assertThat(resource.entryId).isEqualTo(EntryId("eid"))
            assertThat(resource.name).startsWith(resource.type.name.lowercase())
            when(resource.type) {
                ResourceType.DOCUMENT -> {
                    assertThat(resource.extension).isEqualTo(HTML)
                    assertThat(resource.size).isEqualTo(data1.size.toLong())
                    assertFileContents(resourceManager.constructPath(EntryId("eid"), ResourceId("${resource.id}.${resource.extension}")).toString(), data1)
                }
                ResourceType.THUMBNAIL -> {
                    assertThat(resource.extension).isEqualTo(JPG)
                    assertThat(resource.size).isEqualTo(data2.size.toLong())
                    assertFileContents(resourceManager.constructPath(EntryId("eid"), ResourceId("${resource.id}.${resource.extension}")).toString(), data2)
                }
                else -> fail("wrong type")
            }
        }

        // check files moved to main area
        assertFileCount(resourceManager.constructPath(EntryId("eid"), ResourceId("")).toString(), 2)

        // check temp files
        assertThat(fileExists(path1)).isFalse()
        assertThat(fileExists(path2)).isFalse()
    }

    @Test
    fun testMigrateResourcesDoesntExist() {
        val generatedResources = listOf(GeneratedResource(ResourceType.THUMBNAIL, "invalid.txt", TEXT))
        val resources = resourceManager.migrateGeneratedResources(EntryId("eid"), generatedResources)
        assertThat(resources).isEmpty()
    }

    @Test
    fun testMigrateResourcesMissingFileRollsBack() {
        val entryId = EntryId("eid")
        val tempPath = Paths.get(Environment.resource.resourceTempPath, "temp1.txt")
        Files.createDirectories(tempPath.parent)
        Files.write(tempPath, byteArrayOf(1, 2, 3))
        val missingPath = Paths.get(Environment.resource.resourceTempPath, "missing.txt")
        val generatedResources = listOf(
            GeneratedResource(ResourceType.DOCUMENT, tempPath.toString(), TEXT),
            GeneratedResource(ResourceType.DOCUMENT, missingPath.toString(), TEXT)
        )

        val resources = resourceManager.migrateGeneratedResources(entryId, generatedResources)
        assertThat(resources).isEmpty()
        assertThat(Files.exists(tempPath)).isTrue()

        val entryDir = resourceManager.constructPath(entryId, ResourceId("")).toFile()
        assertThat(entryDir.exists()).isFalse()
    }

    @Test
    fun testSaveGeneratedResourceFromTempRollsBackOnDbFailure() {
        val entryId = EntryId("missing")
        val tempPath = Paths.get(Environment.resource.resourceTempPath, "temp2.txt")
        Files.createDirectories(tempPath.parent)
        Files.write(tempPath, byteArrayOf(4, 5, 6))

        assertThrows<Exception> {
            resourceManager.saveGeneratedResource(entryId, ResourceType.DOCUMENT, tempPath)
        }

        assertThat(Files.exists(tempPath)).isTrue()
        val entryDir = resourceManager.constructPath(entryId, ResourceId("")).toFile()
        if (entryDir.exists()) {
            assertThat(entryDir.listFiles().orEmpty()).isEmpty()
        } else {
            assertThat(entryDir.exists()).isFalse()
        }
    }

    @Test
    fun testSaveGeneratedResourceFromBytesRollsBackOnDbFailure() {
        val entryId = EntryId("missing")
        assertThrows<Exception> {
            resourceManager.saveGeneratedResource(entryId, "file.txt", ResourceType.DOCUMENT, byteArrayOf(7, 8, 9))
        }
        val entryDir = resourceManager.constructPath(entryId, ResourceId("")).toFile()
        assertThat(entryDir.exists()).isFalse()
    }

    @Test
    fun testSaveUploadedResourceRollsBackOnDbFailure() {
        val entryId = EntryId("missing")
        assertThrows<Exception> {
            resourceManager.saveUploadedResource(entryId, "upload.txt", byteArrayOf(1, 2, 3).inputStream())
        }
        val entryDir = resourceManager.constructPath(entryId, ResourceId("")).toFile()
        assertThat(entryDir.exists()).isFalse()
    }

    @Test
    fun testConstructPath() {
        val eid = EntryId("id1")
        val file = "file.txt"
        val path = resourceManager.constructPath(eid, ResourceId(file))
        val pathStr = path.toUrlString()
        assertThat(pathStr).startsWith(Environment.resource.resourceBasePath)
        assertThat(path.fileName.toString()).isEqualTo(file)
        assertThat(path.parent.fileName.toString()).isEqualTo(eid.value)
    }

    @Test
    fun testSaveGeneratedResourceNoFile() {
        val filename = "file.txt"
        val extension = "txt"
        val length = 127L
        val resource = resourceManager.saveGeneratedResource(
                entryId = EntryId("eid"),
                name = filename,
                extension = extension,
                size = length,
                type = ResourceType.UPLOAD)
        assertThat(resource.entryId).isEqualTo(EntryId("eid"))
        assertThat(resource.version).isOne()
        assertThat(resource.name).isEqualTo(filename)
        assertThat(resource.extension).isEqualTo(extension)
        assertThat(resource.size).isEqualTo(length)
        assertThat(resource.type).isEqualTo(ResourceType.UPLOAD)

        val retrieved = resourceManager.getResource(resource.id)
        assertThat(retrieved).isEqualTo(resource)

        // save another version
        val resource2 = resourceManager.saveGeneratedResource(
            entryId = EntryId("eid"),
            name = filename,
            extension = extension,
            size = length,
            type = ResourceType.UPLOAD)
        assertThat(resource2.entryId).isEqualTo(EntryId("eid"))
        assertThat(resource2.version).isEqualTo(2)
        assertThat(resource2.name).isEqualTo(filename)
        assertThat(resource2.extension).isEqualTo(extension)
        assertThat(resource2.size).isEqualTo(length)
        assertThat(resource2.type).isEqualTo(ResourceType.UPLOAD)

        val retrieved2 = resourceManager.getResource(resource2.id)
        assertThat(retrieved2).isEqualTo(resource2)
    }

    @Test
    fun testGetResourceById() {
        val resource1 = resourceManager.saveGeneratedResource(ResourceId("rid"), EntryId("eid"), "file1.txt", "txt", ResourceType.UPLOAD, 12L)
        val resource2 = resourceManager.saveGeneratedResource(ResourceId("rid2"), EntryId("eid2"), "file2.html", HTML, ResourceType.SCREENSHOT, 15L)

        val retrieved1 = resourceManager.getResource(ResourceId("rid"))
        assertThat(retrieved1).isNotNull.isEqualTo(resource1)
        assertThat(retrieved1).isEqualTo(Resource(ResourceId("rid"), resource1.parentId, EntryId("eid"), 1, "file1.txt", "txt", ResourceType.UPLOAD, 12L, resource1.dateCreated))

        val retrieved2 = resourceManager.getResource(ResourceId("rid2"))
        assertThat(retrieved2).isNotNull.isEqualTo(resource2)
        assertThat(retrieved2).isEqualTo(Resource(ResourceId("rid2"), resource2.parentId, EntryId("eid2"), 1, "file2.html", HTML, ResourceType.SCREENSHOT, 15L, resource2.dateCreated))
    }

    @Test
    fun testGetResourcesForEntry() {
        val resource1 = resourceManager.saveGeneratedResource(ResourceId("rid"), EntryId("eid"), "file1.txt", "txt", ResourceType.UPLOAD, 12L)
        val resource12 = resourceManager.saveGeneratedResource(ResourceId("rid12"), EntryId("eid"), "file1.txt", "txt", ResourceType.UPLOAD, 15L)
        val resource13 = resourceManager.saveGeneratedResource(ResourceId("rid13"), EntryId("eid"), "file1.txt", "txt", ResourceType.UPLOAD, 122L)
        val resource2 = resourceManager.saveGeneratedResource(ResourceId("rid2"), EntryId("eid2"), "file2.html", HTML, ResourceType.SCREENSHOT, 15L)
        val resource3 = resourceManager.saveGeneratedResource(ResourceId("rid3"), EntryId("eid2"), "file3.kt", "kt", ResourceType.DOCUMENT, 22L)

        val e1 = resourceManager.getResourcesFor(EntryId("eid"))
        assertThat(e1).hasSize(3).containsExactly(resource1, resource12, resource13)
        assertThat(e1).extracting("parentId").containsOnly(resource1.parentId)

        val e2 = resourceManager.getResourcesFor(EntryId("eid2"))
        assertThat(e2).hasSize(2).containsExactlyInAnyOrder(resource2, resource3)
    }

    @Test
    fun testSaveGeneratedResourceWithData() {
        val entryId = EntryId("eid")
        val extension = JPG
        val data = byteArrayOf(1,2,3,4,5)
        val data2 = byteArrayOf(5,4,3,2,1)
        val resource = resourceManager.saveGeneratedResource(entryId, "res1.jpg", ResourceType.SCREENSHOT, data)
        val resource2 = resourceManager.saveGeneratedResource(entryId, "res1.jpg", ResourceType.SCREENSHOT, data2)
        assertThat(resource.entryId).isEqualTo(entryId)
        assertThat(resource.version).isOne()
        assertThat(resource.extension).isEqualTo(extension)
        assertThat(resource.size).isEqualTo(data.size.toLong())
        assertThat(resource.type).isEqualTo(ResourceType.SCREENSHOT)
        assertThat(resource.name).isEqualTo("res1.jpg")
        assertThat(resource2.entryId).isEqualTo(entryId)
        assertThat(resource2.version).isEqualTo(2)
        assertThat(resource2.extension).isEqualTo(extension)
        assertThat(resource2.size).isEqualTo(data2.size.toLong())
        assertThat(resource2.type).isEqualTo(ResourceType.SCREENSHOT)
        assertThat(resource2.name).isEqualTo("res1.jpg")

        assertFileContents(resourceManager.constructPath(entryId, ResourceId("${resource.id}.${resource.extension}")).toString(), data)
        assertFileContents(resourceManager.constructPath(entryId, ResourceId("${resource2.id}.${resource2.extension}")).toString(), data2)
        resourceManager.saveGeneratedResource(entryId, "res2.jpg", ResourceType.THUMBNAIL, data)
        assertFileCount(resourceManager.constructPath(entryId, ResourceId("")).toString(), 3)
        assertFileContents(resourceManager.constructPath(entryId, ResourceId("${resource.id}.${resource.extension}")).toString(), data)

        val res = resourceManager.getResourceAsFile(resource.id)
        assertThat(res?.first).isEqualTo(resource)
        assertThat(res?.second?.readBytes()).isEqualTo(data)
        assertThat(res?.second?.name).isEqualTo("${resource.id}.$extension")
        assertThat(resourceManager.getResource(resource.id)).isEqualTo(resource)
        assertThat(res?.second?.toPath()?.toUrlString()).startsWith(Environment.resource.resourceBasePath)
        val res2 = resourceManager.getResourceAsFile(resource2.id)
        assertThat(res2?.first).isEqualTo(resource2)
        assertThat(res2?.second?.readBytes()).isEqualTo(data2)
        assertThat(res2?.second?.name).isEqualTo("${resource2.id}.$extension")
        assertThat(resourceManager.getResource(resource2.id)).isEqualTo(resource2)
        assertThat(res2?.second?.toPath()?.toUrlString()).startsWith(Environment.resource.resourceBasePath)
    }

    @Test
    fun testSaveGeneratedResourceFromFile() {
        val entryId = EntryId("eid")
        val data = byteArrayOf(1,2,3,4,5)
        val path = resourceManager.constructPath(entryId, ResourceId("res1.jpg"))
        path.toFile().apply {
            parentFile.mkdirs()
            createNewFile()
        }
        Files.write(path, data)
        assertFileCount(resourceManager.constructPath(entryId, ResourceId("")).toString(), 1)
        assertFileContents(path.toString(), data)

        val resource = resourceManager.saveGeneratedResource(EntryId("eid"), ResourceType.GENERATED, path)
        assertThat(resource.entryId).isEqualTo(entryId)
        assertThat(resource.version).isOne()
        assertThat(resource.extension).isEqualTo(JPG)
        assertThat(resource.size).isEqualTo(data.size.toLong())
        assertThat(resource.type).isEqualTo(ResourceType.GENERATED)
        assertThat(resource.name).isEqualTo("res1.jpg")

        assertThat(path.toFile().exists()).isFalse()
        val newPath = resourceManager.constructPath(entryId, ResourceId("${resource.id}.${resource.extension}"))
        assertThat(newPath.toFile().exists()).isTrue()
        assertFileCount(resourceManager.constructPath(entryId, ResourceId("")).toString(), 1)
        assertFileContents(newPath.toString(), data)
    }

    @Test
    fun testGetResourceAsFile() {
        val data = byteArrayOf(1,2,3,4,5)
        val resource = resourceManager.saveGeneratedResource(EntryId("eid"), "res.jpg", ResourceType.SCREENSHOT, data)
        val res = resourceManager.getResourceAsFile(resource.id)
        assertThat(res?.second?.readBytes()).isEqualTo(data)
        assertThat(res?.second?.name).isEqualTo("${resource.id}.$JPG")
    }

    @Test
    fun testSaveUploadedResource() {
        val entryId = EntryId("eid")
        val name = "content.txt"
        val data = byteArrayOf(1,2,3)
        val resource = resourceManager.saveUploadedResource(entryId, name, data.inputStream())
        assertThat(resource.entryId).isEqualTo(entryId)
        assertThat(resource.version).isOne()
        assertThat(resource.extension).isEqualTo("txt")
        assertThat(resource.size).isEqualTo(data.size.toLong())
        assertThat(resource.type).isEqualTo(ResourceType.UPLOAD)
        assertThat(resource.name).isEqualTo(name)

        assertThat(resourceManager.getResource(resource.id)).isEqualTo(resource)

        val res = resourceManager.getResourceAsFile(resource.id)
        assertThat(res?.first).isEqualTo(resource)
        assertThat(res?.second?.readBytes()).isEqualTo(data)
        assertThat(res?.second?.name).isEqualTo("${resource.id}.${resource.extension}")
        assertThat(res?.second?.toPath()?.toUrlString()).startsWith(Environment.resource.resourceBasePath)

        // save another version
        val resource2 = resourceManager.saveUploadedResource(entryId, name, data.inputStream())
        assertThat(resource2.entryId).isEqualTo(entryId)
        assertThat(resource2.version).isEqualTo(2)
        assertThat(resource2.extension).isEqualTo("txt")
        assertThat(resource2.size).isEqualTo(data.size.toLong())
        assertThat(resource2.type).isEqualTo(ResourceType.UPLOAD)
        assertThat(resource2.name).isEqualTo(name)

        assertThat(resourceManager.getResource(resource2.id)).isEqualTo(resource2)
    }

    @Test
    fun testUpdateResource() {
        val entryId = EntryId("eid")
        val name = "content.txt"
        val data = byteArrayOf(1,2,3,4,5,6,7,8,9)
        val data2 = byteArrayOf(5,4,3,2,1)
        val resource = resourceManager.saveUploadedResource(entryId, name, data.inputStream())
        val resource2 = resourceManager.saveUploadedResource(entryId, name, data2.inputStream())
        assertThat(resourceManager.getResource(resource.id)).isNotNull()
        assertThat(resourceManager.getResource(resource2.id)).isNotNull()
        assertThat(resource.name).isEqualTo("content.txt")
        assertThat(resource.extension).isEqualTo("txt")
        assertThat(resource.version).isOne()
        assertThat(resource2.version).isEqualTo(2)
        assertFileCount(resourceManager.constructPath(entryId, ResourceId("")).toString(), 2)
        val originalResourceAsFile = resourceManager.getResourceAsFile(resource.id)
        assertThat(originalResourceAsFile?.second?.name).endsWith("${resource.id}.txt")
        assertFileContents(originalResourceAsFile?.second.toString(), data)
        val originalResource2AsFile = resourceManager.getResourceAsFile(resource2.id)
        assertThat(originalResource2AsFile?.second?.name).endsWith("${resource2.id}.txt")
        assertFileContents(originalResource2AsFile?.second.toString(), data2)

        val updateResourceRequest = resource.copy(name="updated.xml")
        val updatedResource = resourceManager.updateResource(updateResourceRequest)
        assertThat(updatedResource).isNotNull()
        assertThat(updatedResource?.id).isEqualTo(resource.id)
        assertThat(updatedResource?.entryId).isEqualTo(entryId)
        assertThat(updatedResource?.version).isOne()
        assertThat(updatedResource?.name).isEqualTo("updated.xml")
        assertThat(updatedResource?.extension).isEqualTo("xml")
        assertThat(updatedResource?.size).isEqualTo(data.size.toLong())
        assertThat(updatedResource?.type).isEqualTo(ResourceType.UPLOAD)

        val retrievedResource = resourceManager.getResource(resource.id)
        assertThat(retrievedResource).isEqualTo(updatedResource)
        assertFileCount(resourceManager.constructPath(entryId, ResourceId("")).toString(), 2)

        val retrievedResource2 = resourceManager.getResource(resource2.id)
        assertThat(retrievedResource2?.version).isEqualTo(2)
        assertThat(retrievedResource2?.parentId).isEqualTo(retrievedResource?.parentId)

        val resourceAsFile = resourceManager.getResourceAsFile(resource.id)
        assertThat(resourceAsFile?.second?.name).endsWith("${resource.id}.xml")
        assertThat(resourceAsFile?.second?.exists()).isTrue()
        assertFileContents(resourceAsFile?.second.toString(), data)
        assertThat(originalResourceAsFile?.second?.exists()).isFalse()

        val resourceAsFile2 = resourceManager.getResourceAsFile(resource2.id)
        assertThat(resourceAsFile2?.second?.name).endsWith("${resource2.id}.xml")
        assertThat(resourceAsFile2?.second?.exists()).isTrue()
        assertFileContents(resourceAsFile2?.second.toString(), data2)
        assertThat(originalResource2AsFile?.second?.exists()).isFalse()
    }

    @Test
    fun testUpdateResourceDoesntExist() {
        val resource = Resource(ResourceId("invalid"), "pid", EntryId("eid"), 1, "file1.txt", "txt", ResourceType.UPLOAD, 12L, 1234)
        val updated = resourceManager.updateResource(resource)
        assertThat(updated).isNull()
    }

    @Test
    fun testUpdateResourceRollbackWhenFileMissing() {
        val entryId = EntryId("eid")
        val data = byteArrayOf(1,2,3,4,5)
        val data2 = byteArrayOf(6,7,8,9,10)
        val resource = resourceManager.saveUploadedResource(entryId, "content.txt", data.inputStream())
        val resource2 = resourceManager.saveUploadedResource(entryId, "content.txt", data2.inputStream())

        val missingPath = resourceManager.constructPath(entryId, ResourceId("${resource2.id}.${resource2.extension}"))
        Files.deleteIfExists(missingPath)

        assertThrows<IllegalStateException> {
            resourceManager.updateResource(resource.copy(name = "updated.xml"))
        }

        val originalPath = resourceManager.constructPath(entryId, ResourceId("${resource.id}.${resource.extension}"))
        val movedPath = resourceManager.constructPath(entryId, ResourceId("${resource.id}.xml"))
        assertThat(Files.exists(originalPath)).isTrue()
        assertThat(Files.exists(movedPath)).isFalse()
    }

    @Test
    fun testDeleteResourceDoesntExist() {
        assertThat(resourceManager.delete(ResourceId("nothing"))).isFalse()
    }

    @Test
    fun testDeleteAllForEntryDoesntExist() {
        // still successfully deleted everything
        assertThat(resourceManager.deleteAll(EntryId("nothing"))).isTrue()
    }

    @Test
    fun testDeleteLastResourceVersion() {
        val entryId = EntryId("eid")
        val data = byteArrayOf(1,2,3,4,5)
        val resource = resourceManager.saveGeneratedResource(entryId, "res.jpg", ResourceType.SCREENSHOT, data)
        assertThat(resourceManager.getResource(resource.id)).isNotNull()
        assertFileCount(resourceManager.constructPath(entryId, ResourceId("")).toString(), 1)
        assertThat(resourceManager.delete(resource.id)).isTrue()
        assertThat(resourceManager.getResource(resource.id)).isNull()
        assertFileCount(resourceManager.constructPath(entryId, ResourceId("")).toString(), 0)
    }

    @Test
    fun testDeleteResourceVersion() {
        val entryId = EntryId("eid")
        val data = byteArrayOf(1,2,3,4,5)
        val resource = resourceManager.saveGeneratedResource(entryId, "res.jpg", ResourceType.SCREENSHOT, data)
        val resource2 = resourceManager.saveGeneratedResource(entryId, "res.jpg", ResourceType.SCREENSHOT, data)
        assertThat(resourceManager.getResource(resource.id)).isNotNull()
        assertThat(resourceManager.getResource(resource2.id)).isNotNull()
        assertFileCount(resourceManager.constructPath(entryId, ResourceId("")).toString(), 2)
        assertThat(resourceManager.delete(resource2.id)).isTrue()
        assertThat(resourceManager.getResource(resource.id)).isNotNull()
        assertThat(resourceManager.getResource(resource2.id)).isNull()
        assertFileCount(resourceManager.constructPath(entryId, ResourceId("")).toString(), 1)
    }

    @Test
    fun testDeleteEntry() {
        val entryId = EntryId("eid")
        val data = byteArrayOf(1,2,3,4,5)
        val data2 = byteArrayOf(5,6,7,8,9)
        val resource = resourceManager.saveGeneratedResource(entryId, "res1.jpg", ResourceType.THUMBNAIL, data)
        val resource2 = resourceManager.saveGeneratedResource(entryId, "res2.png", ResourceType.SCREENSHOT, data2)

        assertThat(resourceManager.getResource(resource.id)).isNotNull()
        assertThat(resourceManager.getResource(resource2.id)).isNotNull()

        assertFileCount(resourceManager.constructPath(entryId, ResourceId("")).toString(), 2)
        assertThat(resourceManager.deleteAll(entryId)).isTrue()
        assertThat(resourceManager.getResource(resource.id)).isNull()
        assertThat(resourceManager.getResource(resource2.id)).isNull()
        assertThat(Files.exists(resourceManager.constructPath(entryId, ResourceId("")))).isFalse()
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

    private fun tempFileToFullPath(path: String) = Paths.get(Environment.resource.resourceTempPath).resolve(Paths.get(path))

}
