package lynks.util.markdown

import com.vladsch.flexmark.util.ast.DoNotDecorate
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.sequence.BasedSequence

internal class EntryLinkNode(private val openingMarker: BasedSequence, var text: BasedSequence) :
    Node(spanningChars(openingMarker, text)),
    DoNotDecorate {

    override fun getSegments(): Array<BasedSequence> {
        return arrayOf(openingMarker, text)
    }

    override fun getAstExtra(out: StringBuilder) {
        delimitedSegmentSpanChars(
            out,
            openingMarker,
            text,
            BasedSequence.NULL,
            "text"
        )
    }
}
