package link.extract

import org.jsoup.Jsoup

object ExtractUtils {

    fun extractTextFromHtmlDoc(html: String?): String? {
        if (html == null) return null
        if (!html.startsWith("<")) return html
        val document = Jsoup.parse(html)
        return document.text()
    }

}