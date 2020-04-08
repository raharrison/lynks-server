package link

import com.chimbori.crux.articles.Article
import com.chimbori.crux.articles.ArticleExtractor
import common.BaseProperties
import io.webfolder.cdp.AdaptiveProcessManager
import io.webfolder.cdp.Launcher
import io.webfolder.cdp.event.Events
import io.webfolder.cdp.event.network.ResponseReceived
import io.webfolder.cdp.session.Session
import resource.JPG
import resource.PDF
import resource.PNG
import resource.ResourceType
import task.DiscussionFinderTask
import task.LinkProcessingTask
import task.LinkSummarizerTask
import util.loggerFor
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

data class ImageResource(val image: ByteArray, val extension: String) {

    override fun equals(other: Any?): Boolean {
        if (other != null && other is ImageResource) {
            return image contentEquals other.image && extension == other.extension
        }
        return false
    }

    override fun hashCode(): Int {
        return Objects.hash(image.contentHashCode(), extension)
    }
}

interface LinkProcessor : AutoCloseable {

    suspend fun init()

    fun matches(): Boolean

    suspend fun generateThumbnail(): ImageResource?

    suspend fun generateScreenshot(): ImageResource?

    suspend fun printPage(): ImageResource?

    suspend fun enrich(props: BaseProperties) {
        props.addTask("Process Link", LinkProcessingTask.build())
        props.addTask("Find Discussions", DiscussionFinderTask.build())
        props.addTask("Generate Summary", LinkSummarizerTask.build())
    }

    val html: String?

    val content: String?

    val title: String

    val resolvedUrl: String

    val keywords: Set<String>

}

private val log = loggerFor<DefaultLinkProcessor>()

open class DefaultLinkProcessor(private val url: String) : LinkProcessor {

    private var session: Session? = null

    private val article = lazy {
        extractArticle()
    }

    override suspend fun init() {
        session = connectSession(url)
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
        if (statuses.size > 0 && statuses.first() == 200)
            return session
        else {
            log.error("Unable to navigate to url={} response statuses={}", url, statuses)
            session.close()
            throw IllegalArgumentException("Could not navigate to $url")
        }
    }

    private fun extractArticle(): Article {
        log.info("Performing article extraction for url={}", resolvedUrl)
        return ArticleExtractor.with(resolvedUrl, html)
            .extractMetadata()
            .extractContent()
            .article()
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
        props.addTask("Generate Screenshot", LinkProcessingTask.build(ResourceType.SCREENSHOT))
        props.addTask("Generate Document", LinkProcessingTask.build(ResourceType.DOCUMENT))
        props.addTask("Generate Thumbnail", LinkProcessingTask.build(ResourceType.THUMBNAIL))
    }

    override fun matches(): Boolean = true

    override suspend fun generateThumbnail(): ImageResource {
        if (session == null) throw sessionNotInit()
        synchronized(session!!) {
            log.info("Capturing thumbnail for url={}", resolvedUrl)
            val screen = session?.command?.page?.captureScreenshot()
            val img = ImageIO.read(ByteArrayInputStream(screen))
            val scaledImage = img.getScaledInstance(640, 360, Image.SCALE_SMOOTH)
            val imageBuff = BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB)
            imageBuff.graphics.drawImage(scaledImage, 0, 0, Color.BLACK, null)
            imageBuff.graphics.dispose()
            val buffer = ByteArrayOutputStream()
            ImageIO.write(imageBuff, "jpg", buffer)
            return ImageResource(buffer.toByteArray(), JPG)
        }
    }

    override suspend fun generateScreenshot(): ImageResource {
        if (session == null) throw sessionNotInit()
        synchronized(session!!) {
            log.info("Capturing full page screenshot for url={}", resolvedUrl)
            return ImageResource(session?.captureScreenshot(true) ?: throw sessionNotInit(), PNG)
        }
    }

    override suspend fun printPage(): ImageResource? {
        log.info("Capturing pdf for url={}", resolvedUrl)
        return ImageResource(
            session?.command?.page?.printToPDF(
                true, false, true,
                0.9, 11.7, 16.5,
                0.1, 0.1, 0.1, 0.1,
                null, false, null, null, false
            )
                ?: throw sessionNotInit(), PDF
        )
    }

    override val html: String get() = session?.content ?: throw sessionNotInit()

    override val content: String? get() = article.value.document.toString()

    override val title: String get() = session?.title ?: throw sessionNotInit()

    override val resolvedUrl: String get() = session?.location ?: throw sessionNotInit()

    override val keywords: Set<String> get() = article.value.keywords.toSet()

    private fun sessionNotInit() = IllegalStateException("Web session has not been initialised")

}
