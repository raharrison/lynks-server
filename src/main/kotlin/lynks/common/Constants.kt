package lynks.common

// varchar length of generated uids
const val UID_LENGTH = 16

// path the temporary files
val TEMP_URL = "${Environment.server.rootPath}/temp/"

// maximum size of image uploads
const val MAX_IMAGE_UPLOAD_BYTES = 1024 * 1024 * 10 // 10MB

// pasted images waiting for their entry to be saved
const val TEMP_UPLOAD_DIR = "uploads"
val TEMP_UPLOAD_URL = "$TEMP_URL$TEMP_UPLOAD_DIR/"

// allowed image upload extensions
val ALLOWED_IMAGE_EXTENSIONS = setOf("jpg", "png", "gif", "webp")

// property key to designate dead links
const val DEAD_LINK_PROP = "dead"

// property key for discussions
const val DISCUSSIONS_PROP = "discussions"

const val MDC_REQUEST_ID = "requestId"
