package link

sealed class GeneratedResource

class GeneratedImageResource(val image: ByteArray, val extension: String) :
    GeneratedResource()

class GeneratedDocResource(val doc: String, val extension: String) : GeneratedResource()
