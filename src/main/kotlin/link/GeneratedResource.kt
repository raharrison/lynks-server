package link

import java.util.*

sealed class GeneratedResource

data class GeneratedImageResource(val image: ByteArray, val extension: String) :
    GeneratedResource() {

    override fun equals(other: Any?): Boolean {
        if (other != null && other is GeneratedImageResource) {
            return image contentEquals other.image && extension == other.extension
        }
        return false
    }

    override fun hashCode(): Int {
        return Objects.hash(image.contentHashCode(), extension)
    }
}

data class GeneratedDocResource(val doc: String, val extension: String) : GeneratedResource()
