package lynks.util

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.io.ClassPathTemplateLoader

class ResourceTemplater(location: String) {

    private val template = handlebars.compile(location)

    fun apply(context: Map<*, *>): String {
        return template.apply(context)
    }

    companion object {
        private val loader = ClassPathTemplateLoader().apply {
            this.prefix = "/templates"
            this.suffix = ""
        }

        private val handlebars = Handlebars(loader)
    }

}
