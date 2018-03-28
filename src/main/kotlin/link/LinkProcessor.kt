package link

import io.webfolder.cdp.AdaptiveProcessManager
import io.webfolder.cdp.Launcher
import io.webfolder.cdp.session.Session
import util.URLUtils
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO


// as actor via queue of actions

class LinkProcessorWorker

interface LinkProcessor {

    fun matches(): Boolean

    fun generateThumbnail(): ByteArray

    fun generateScreenshot(): ByteArray

    val html: String

    val title: String

    val resolvedUrl: String

}


open class DefaultLinkProcessor(private val url: String) : LinkProcessor, AutoCloseable {

    private val session: Session by lazy {
        connectSession()
    }

    private fun connectSession(): Session {
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

    override fun matches(): Boolean = true

    override fun generateThumbnail(): ByteArray {
        val screen = session.command.page.captureScreenshot()
        val img = ImageIO.read(ByteArrayInputStream(screen))
        val scaledImage = img.getScaledInstance(360, 270, Image.SCALE_SMOOTH)
        val imageBuff = BufferedImage(360, 270, BufferedImage.TYPE_INT_RGB)
        imageBuff.graphics.drawImage(scaledImage, 0, 0, Color.BLACK, null)
        imageBuff.graphics.dispose()
        val buffer = ByteArrayOutputStream()
        ImageIO.write(imageBuff, "jpg", buffer)
        return buffer.toByteArray()
    }

    override fun generateScreenshot(): ByteArray = session.captureScreenshot()

    override val html: String = session.content

    override val title: String = session.title

    override val resolvedUrl: String = session.location

}

class YoutubeLinkProcessor(private val url: String): DefaultLinkProcessor(url) {

    override fun matches(): Boolean = URLUtils.extractSource(url) == "youtube.com"

    override fun generateThumbnail(): ByteArray {
        TODO("not implemented")
    }

    override fun generateScreenshot(): ByteArray {
        TODO("not implemented")
    }
}