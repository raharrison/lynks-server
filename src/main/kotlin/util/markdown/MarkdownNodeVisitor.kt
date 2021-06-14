package util.markdown

interface MarkdownNodeVisitor {

    val pattern: String

    fun replace(match: MatchResult): String?

}
