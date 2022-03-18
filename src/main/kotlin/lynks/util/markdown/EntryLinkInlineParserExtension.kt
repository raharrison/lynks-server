package lynks.util.markdown

import com.vladsch.flexmark.parser.InlineParser
import com.vladsch.flexmark.parser.InlineParserExtension
import com.vladsch.flexmark.parser.InlineParserExtensionFactory
import com.vladsch.flexmark.parser.LightInlineParser
import java.util.regex.Pattern

internal class EntryLinkInlineParserExtension : InlineParserExtension {
    override fun finalizeDocument(inlineParser: InlineParser) {}
    override fun finalizeBlock(inlineParser: InlineParser) {}

    override fun parse(inlineParser: LightInlineParser): Boolean {
        val index = inlineParser.index
        var isPossible = index == 0
        if (!isPossible) {
            val c = inlineParser.input[index - 1]
            if (!Character.isUnicodeIdentifierPart(c) && c != '-' && c != '.') {
                isPossible = true
            }
        }
        if (isPossible) {
            val matches = inlineParser.matchWithGroups(ENTRY_LINK_PATTERN)
            if (matches != null) {
                inlineParser.flushTextNode()
                val openMarker = matches[1]
                val text = matches[2]
                val entryLinkNode = EntryLinkNode(openMarker!!, text!!)
                inlineParser.block.appendChild(entryLinkNode)
                return true
            }
        }
        return false
    }

    companion object {
        private val ENTRY_LINK_PATTERN = Pattern.compile(
            "^(@)([a-z\\d-_](?:[a-z\\d-_]|-(?=[a-z\\d-_])){0,14})(?![\\w-])",
            Pattern.CASE_INSENSITIVE
        )
    }

    internal class Factory : InlineParserExtensionFactory {
        override fun getAfterDependents(): Set<Class<*>>? {
            return null
        }

        override fun getCharacters(): CharSequence {
            return "@"
        }

        override fun getBeforeDependents(): Set<Class<*>>? {
            return null
        }

        override fun apply(lightInlineParser: LightInlineParser): InlineParserExtension {
            return EntryLinkInlineParserExtension()
        }

        override fun affectsGlobalScope(): Boolean {
            return false
        }
    }
}
