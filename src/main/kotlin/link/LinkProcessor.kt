package link

import common.BaseProperties
import io.webfolder.cdp.AdaptiveProcessManager
import io.webfolder.cdp.Launcher
import io.webfolder.cdp.session.Session
import resource.JPG
import resource.PNG
import util.URLUtils
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

data class ImageResource(val image: ByteArray, val extension: String)

interface LinkProcessor: AutoCloseable {

    fun init(url: String)

    fun matches(url: String): Boolean

    fun generateThumbnail(): ImageResource?

    fun generateScreenshot(): ImageResource?

    fun enrich(props: BaseProperties)

    val html: String?

    val title: String

    val resolvedUrl: String

}


open class DefaultLinkProcessor : LinkProcessor {

    protected lateinit var session: Session
    protected lateinit var url: String

    override fun init(url: String) {
        this.url = url
        session = connectSession(url)
    }

    private fun connectSession(url: String): Session {
        val factory = launcher.launch(listOf("--headless", "--disable-gpu"))
        val session = factory.create()
        session.navigate(url)
        session.waitDocumentReady()
        session.wait(1000)
        session.activate()
        return session
    }

    companion object {
        val launcher: Launcher = Launcher()
        init {
            launcher.processManager = AdaptiveProcessManager()
        }
    }

    override fun close() = session.close()

    override fun matches(url: String): Boolean = true

    override fun generateThumbnail(): ImageResource {
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

    override fun generateScreenshot(): ImageResource = ImageResource(session.captureScreenshot(), PNG)

    override fun enrich(props: BaseProperties) {
    }

    override val html: String = session.content

    override val title: String = session.title

    override val resolvedUrl: String = session.location

}

class YoutubeLinkProcessor: LinkProcessor {

    private lateinit var url: String
    private lateinit var videoId: String

    override fun init(url: String) {
        this.url = url
        this.videoId = URLUtils.extractQueryParams(url)["v"] ?: throw IllegalArgumentException("Invalid youtube url")
    }

    override val html: String? = null
    override val title: String = "title"
    override val resolvedUrl: String = url

    override fun close() {
    }

    override fun matches(url: String): Boolean = URLUtils.extractSource(url) == "youtube.com"

    override fun generateThumbnail(): ImageResource {
        val dl = "http://img.youtube.com/vi/$videoId/0.jpg"
        TODO("not implemented")
    }

    override fun generateScreenshot(): ImageResource {
        val dl = "http://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        TODO("not implemented")
    }

    override fun enrich(props: BaseProperties) {
        TODO("not implemented")
    }
}