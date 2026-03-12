package lynks.common

// varchar length of generated uids
const val UID_LENGTH = 14

// path the temporary files
val TEMP_URL = "${Environment.server.rootPath}/temp/"

// maximum size of image uploads
const val MAX_IMAGE_UPLOAD_BYTES = 1024 * 1024 * 10 // 10MB

// resource name for image uploads
const val IMAGE_UPLOAD_BASE = "imageUpload"

// allowed image upload extensions
val ALLOWED_IMAGE_EXTENSIONS = setOf("jpg", "png", "gif", "webp")

// property key to designate dead links
const val DEAD_LINK_PROP = "dead"

// property key for discussions
const val DISCUSSIONS_PROP = "discussions"

const val MDC_REQUEST_ID = "requestId"
