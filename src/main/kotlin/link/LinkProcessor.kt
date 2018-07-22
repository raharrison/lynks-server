package link

import com.chimbori.crux.articles.ArticleExtractor
import common.BaseProperties
import io.webfolder.cdp.AdaptiveProcessManager
import io.webfolder.cdp.Launcher
import io.webfolder.cdp.event.Events
import io.webfolder.cdp.event.network.ResponseReceived
import io.webfolder.cdp.session.Session
import io.webfolder.cdp.type.page.ResourceType
import resource.JPG
import resource.PDF
import resource.PNG
import task.LinkProcessingTask
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

data class ImageResource(val image: ByteArray, val extension: String) {

    override fun equals(other: Any?): Boolean {
        if(other != null && other is ImageResource) {
            return image contentEquals other.image && extension == other.extension
        }
        return false
    }

    override fun hashCode(): Int {
        return Objects.hash(image.contentHashCode(), extension)
    }
}

interface LinkProcessor : AutoCloseable {

    suspend fun init(url: String)

    fun matches(url: String): Boolean

    suspend fun generateThumbnail(): ImageResource?

    suspend fun generateScreenshot(): ImageResource?

    suspend fun printPage(): ImageResource?

    suspend fun enrich(props: BaseProperties) {
        props.addTask("Process Link", LinkProcessingTask.build())
    }

    val html: String?

    val content: String?

    val title: String

    val resolvedUrl: String

}


open class DefaultLinkProcessor : LinkProcessor {

    protected lateinit var session: Session
    protected lateinit var url: String

    override suspend fun init(url: String) {
        this.url = url
        session = connectSession(url)
    }

    private fun connectSession(url: String): Session {
        val session = sessionFactory.value.create()
        val statuses = mutableListOf<Int>()
        session.command.network.enable()
        session.addEventListener { event, value ->
            if (Events.NetworkResponseReceived == event) {
                val rr = value as ResponseReceived
                if(rr.type == ResourceType.Document) {
                    statuses.add(rr.response.status)
                }
            }
        }
        session.navigate(url)
        session.waitDocumentReady()
        session.wait(1000)
        session.activate()
        if(statuses.size > 0 && statuses.first() == 200)
            return session
        else {
            session.close()
            throw IllegalArgumentException("Could not navigate to $url")
        }
    }

    companion object {
        private val launcher: Launcher = Launcher()
        private val sessionFactory = lazy {
            launcher.launch(listOf("--headless", "--disable-gpu"))
        }

        init {
            launcher.processManager = AdaptiveProcessManager()
            Runtime.getRuntime().addShutdownHook(Thread {
                if(sessionFactory.isInitialized())
                    sessionFactory.value.close()
                launcher.processManager.kill()
            })
        }
    }

    override fun close() = session.close()

    override fun matches(url: String): Boolean = true

    override suspend fun generateThumbnail(): ImageResource {
        val screen = session.command.page.captureScreenshot()
        val img = ImageIO.read(ByteArrayInputStream(screen))
        val scaledImage = img.getScaledInstance(360, 270, Image.SCALE_SMOOTH)
        val imageBuff = BufferedImage(360, 270, BufferedImage.TYPE_INT_RGB)
        imageBuff.graphics.drawImage(scaledImage, 0, 0, Color.BLACK, null)
        imageBuff.graphics.dispose()
        val buffer = ByteArrayOutputStream()
        ImageIO.write(imageBuff, "jpg", buffer)
        return ImageResource(buffer.toByteArray(), JPG)
    }

    override suspend fun generateScreenshot(): ImageResource = ImageResource(session.captureScreenshot(), PNG)

    override suspend fun printPage(): ImageResource? {
        return ImageResource(session.command.page.printToPDF(true, false, true,
                0.9, 11.7, 16.5,
                0.1, 0.1, 0.1, 0.1,
                null, false, null, null, false), PDF)
    }

    override val html: String get() = session.content

    override val content: String?
        get() {
            val article = ArticleExtractor.with(resolvedUrl, html)
                    .extractMetadata()
                    .extractContent()
                    .article()
            return article.document.toString()
        }

    override val title: String get() = session.title

    override val resolvedUrl: String get() = session.location

}
