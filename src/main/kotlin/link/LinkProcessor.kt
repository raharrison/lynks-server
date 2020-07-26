package link

import common.BaseProperties
import io.webfolder.cdp.AdaptiveProcessManager
import io.webfolder.cdp.Launcher
import io.webfolder.cdp.event.Events
import io.webfolder.cdp.event.network.ResponseReceived
import io.webfolder.cdp.session.Session
import kotlinx.coroutines.runBlocking
import link.extract.ExtractionPolicy
import link.extract.FullLinkContentExtractor
import link.extract.LinkContent
import link.extract.PartialLinkContentExtractor
import resource.*
import resource.ResourceType.*
import task.DiscussionFinderTask
import task.LinkProcessingTask
import task.LinkSummarizerTask
import util.FileUtils
import util.ImageUtils
import util.loggerFor
import java.util.*

abstract class LinkProcessor(
    protected val extractionPolicy: ExtractionPolicy,
    protected val url: String,
    protected val resourceRetriever: ResourceRetriever
) :
    AutoCloseable {

    abstract suspend fun init()

    abstract fun matches(): Boolean

    abstract val linkContent: LinkContent

    abstract suspend fun process(resourceSet: EnumSet<ResourceType>): Map<ResourceType, GeneratedResource>

    open suspend fun enrich(props: BaseProperties) {
        props.addTask("Process Link", LinkProcessingTask.build())
        props.addTask("Find Discussions", DiscussionFinderTask.build())
    }

    abstract val resolvedUrl: String

}

open class DefaultLinkProcessor(
    extractionPolicy: ExtractionPolicy,
    url: String,
    resourceRetriever: ResourceRetriever
) :
    LinkProcessor(extractionPolicy, url, resourceRetriever) {

    private val log = loggerFor<DefaultLinkProcessor>()

    private var session: Session? = null

    private val html: String by lazy {
        retrieveHtml()
    }

    override suspend fun init() {
        if (extractionPolicy == ExtractionPolicy.FULL) {
            session = connectSession(url)
        }
    }

    private fun connectSession(url: String): Session {
        val session = sessionFactory.value.create()
        val statuses = mutableListOf<Int>()
        session.command.network.enable()
        session.addEventListener { event, value ->
            if (Events.NetworkResponseReceived == event) {
                val rr = value as ResponseReceived
                if (rr.type == io.webfolder.cdp.type.network.ResourceType.Document) {
                    statuses.add(rr.response.status)
                }
            }
        }
        log.info("Navigating headless browser to url={}", url)
        session.navigate(url)
        session.waitDocumentReady()
        session.wait(1000)
        session.activate()
        if (statuses.size > 0 && (statuses.first() == 200 || statuses.first() % 300 < 100))
            return session
        else {
            log.error("Unable to navigate to url={} response statuses={}", url, statuses)
            session.close()
            throw IllegalArgumentException("Could not navigate to $url")
        }
    }

    companion object {
        private val launcher: Launcher = Launcher()
        private val sessionFactory = lazy {
            launcher.launch(listOf("--headless", "--disable-gpu", "--hide-scrollbars", "--window-size=1280,720"))
        }

        init {
            launcher.processManager = AdaptiveProcessManager()
            Runtime.getRuntime().addShutdownHook(Thread {
                if (sessionFactory.isInitialized())
                    sessionFactory.value.close()
                launcher.processManager.kill()
            })
        }
    }

    override fun close() {
        session?.close()
    }

    override suspend fun enrich(props: BaseProperties) {
        super.enrich(props)
        props.addTask("Generate Screenshot", LinkProcessingTask.build(SCREENSHOT))
        props.addTask("Generate Document", LinkProcessingTask.build(DOCUMENT))
        props.addTask("Generate Summary", LinkSummarizerTask.build())
    }

    override fun matches(): Boolean = true

    override suspend fun process(resourceSet: EnumSet<ResourceType>): Map<ResourceType, GeneratedResource> {
        val generatedResources = mutableMapOf<ResourceType, GeneratedResource>()

        if (resourceSet.contains(PREVIEW) || resourceSet.contains(THUMBNAIL)) {
            val preview = generatePreview(linkContent)
            addResource(generatedResources, PREVIEW, preview)
            addResource(generatedResources, THUMBNAIL, generateThumbnail(preview))
        }
        if (resourceSet.contains(READABLE)) {
            linkContent.extractedContent?.let {
                addResource(generatedResources, READABLE, GeneratedDocResource(it, HTML))
            }
        }
        if (resourceSet.contains(SCREENSHOT)) {
            addResource(generatedResources, SCREENSHOT, generateScreenshot())
        }
        if (resourceSet.contains(PAGE)) {
            addResource(generatedResources, PAGE, GeneratedDocResource(html, HTML))
        }
        if (resourceSet.contains(DOCUMENT)) {
            addResource(generatedResources, DOCUMENT, printPage())
        }

        return generatedResources
    }

    private fun addResource(
        resources: MutableMap<ResourceType, GeneratedResource>,
        resourceType: ResourceType,
        resource: GeneratedResource?
    ) {
        if (resource != null) {
            resources[resourceType] = resource
        } else {
            log.info("Generated resource for {} is empty, not adding to results", resourceType)
        }
    }

    override val linkContent: LinkContent by lazy {
        val linkContentExtractor =
            if (extractionPolicy == ExtractionPolicy.FULL) FullLinkContentExtractor()
            else PartialLinkContentExtractor()
        linkContentExtractor.extractContent(resolvedUrl, html)
    }

    private suspend fun generatePreview(linkContent: LinkContent): GeneratedImageResource? {
        if (extractionPolicy == ExtractionPolicy.FULL) {
            if (session == null) throw sessionNotInit()
            synchronized(session!!) {
                log.info("Capturing thumbnail for url={}", resolvedUrl)
                return session?.command?.page?.captureScreenshot()?.let {
                    val image = ImageUtils.scaleToDimensions(it, 640, 360)
                    return GeneratedImageResource(image, JPG)
                }
            }
        } else if (linkContent.imageUrl != null) {
            val mainImage = resourceRetriever.getFile(linkContent.imageUrl)
            if (mainImage != null) {
                val image = ImageUtils.scaleToDimensions(mainImage, 640, 360)
                return GeneratedImageResource(image, FileUtils.getExtension(linkContent.imageUrl))
            }
        }
        return null
    }

    private fun generateScreenshot(): GeneratedResource {
        if (session == null) throw sessionNotInit()
        synchronized(session!!) {
            log.info("Capturing full page screenshot for url={}", resolvedUrl)
            return GeneratedImageResource(session?.captureScreenshot(true) ?: throw sessionNotInit(), PNG)
        }
    }

    private fun generateThumbnail(preview: GeneratedImageResource?): GeneratedResource? {
        if (preview == null) return null
        val thumbnail = ImageUtils.cropImage(preview.image, 320, 180)
        return GeneratedImageResource(thumbnail, preview.extension)
    }

    private fun printPage(): GeneratedResource {
        log.info("Capturing pdf for url={}", resolvedUrl)
        return GeneratedImageResource(
            session?.command?.page?.printToPDF(
                true, false, true,
                0.9, 11.7, 16.5,
                0.1, 0.1, 0.1, 0.1,
                null, false, null, null, false
            ) ?: throw sessionNotInit(), PDF
        )
    }

    private fun retrieveHtml(): String {
        return if (extractionPolicy == ExtractionPolicy.FULL) {
            session?.content ?: throw sessionNotInit()
        } else {
            return runBlocking {
                resourceRetriever.getString(resolvedUrl)
                    ?: throw java.lang.IllegalStateException("Unable to retrieve page details")
            }
        }
    }

    override val resolvedUrl: String
        get() {
            return if (extractionPolicy == ExtractionPolicy.FULL) {
                session?.location ?: throw sessionNotInit()
            } else {
                url
            }
        }

    private fun sessionNotInit() = IllegalStateException("Web session has not been initialised")

}
