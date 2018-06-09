package link

import common.BaseProperties
import io.webfolder.cdp.AdaptiveProcessManager
import io.webfolder.cdp.Launcher
import io.webfolder.cdp.session.Session
import resource.JPG
import resource.PDF
import resource.PNG
import task.LinkProcessingTask
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

data class ImageResource(val image: ByteArray, val extension: String)

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

    override suspend  fun generateThumbnail(): ImageResource {
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

    override val title: String get() = session.title

    override val resolvedUrl: String get() = session.location

}
