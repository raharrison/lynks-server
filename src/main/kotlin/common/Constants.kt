package common

// path the temporary files
val TEMP_URL = "${Environment.server.rootPath}/temp/"

// maximum size of image uploads
const val MAX_IMAGE_UPLOAD_BYTES = 1024 * 1024 * 5 // 5MB

// resource name for image uploads
const val IMAGE_UPLOAD_BASE = "imageUpload"

// property key to designate dead links
const val DEAD_LINK_PROP = "dead"

// property key to designate read links
const val READ_LINK_PROP = "read"

// property key for discussions
const val DISCUSSIONS_PROP = "discussions"
