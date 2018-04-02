//package link
//
//import com.chimbori.crux.articles.ArticleExtractor
//import io.webfolder.cdp.AdaptiveProcessManager
//import io.webfolder.cdp.Launcher
//import io.webfolder.cdp.session.Session
//import java.awt.Color
//import java.awt.Image
//import java.awt.image.BufferedImage
//import java.io.ByteArrayInputStream
//import java.io.ByteArrayOutputStream
//import javax.imageio.ImageIO
//
//class WebpageExtractor(private val url: String) : AutoCloseable {
//
//    private val session: Session by lazy {
//        connectSession()
//    }
//
//    override fun close() {
//        session.close()
//    }
//
//    fun generateScreenshot(): ByteArray {
//        return session.captureScreenshot()
//    }
//
//    fun generateThumbnail(): ByteArray {
//        val screen = session.command.page.captureScreenshot()
//        val img = ImageIO.read(ByteArrayInputStream(screen))
//        val scaledImage = img.getScaledInstance(360, 270, Image.SCALE_SMOOTH)
//        val imageBuff = BufferedImage(360, 270, BufferedImage.TYPE_INT_RGB)
//        imageBuff.graphics.drawImage(scaledImage, 0, 0, Color.BLACK, null)
//        imageBuff.graphics.dispose()
//        val buffer = ByteArrayOutputStream()
//        ImageIO.write(imageBuff, "jpg", buffer)
//        return buffer.toByteArray()
//    }
//
//    val html : String get() = session.content
//
//    val title : String get() = session.title
//
//    val resolvedUrl : String get() = session.location
//
//    val article get() = {
//        ArticleExtractor.with(url, html)
//                .extractMetadata()
//                .extractContent()
//                .article()
//    }
//
//    private fun connectSession(): Session {
//        val factory = launcher.launch(listOf("--headless", "--disable-gpu"))
//        val session = factory.create()
//        session.navigate(url)
//        session.waitDocumentReady()
//        session.wait(1000)
//        session.activate()
//        return session
//    }
//
//    companion object {
//        val launcher: Launcher = Launcher()
//        init {
//            launcher.processManager = AdaptiveProcessManager()
//        }
//
//    }
//
//}