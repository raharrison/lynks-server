import com.chimbori.crux.articles.ArticleExtractor
import util.ImageUtils
import java.io.ByteArrayInputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO

fun main() {

//    val url = "https://ryanharrison.co.uk/2020/04/12/kotlin-java-ci-with-github-actions.html"
//    val content = URL(url).readText()
//
//    val article = ArticleExtractor.with(url, content)
//        .extractMetadata()
//        .extractContent()
//        .estimateReadingTime()
//        .article()
//
//    println(article.title)
//    println(article.document.toString())
//    println(article.keywords)

    val url = "https://ryanharrison.co.uk/images/2019/android-sms-backup.jpg"
    val raw = URL(url).readBytes()

//    val output = ImageUtils.cropImage(raw,320, 180)
    val img = ImageIO.read(ByteArrayInputStream(raw))
    Download.saveScaledImage(img, 320, 180, "output.jpg")

//    Files.write(Paths.get("output.jpg"), output)

}

object Scratch {

}
