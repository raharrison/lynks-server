package resource

// {"data": {"filePath": "<filePath>"}}
data class ImageUploadFilePath(val filePath: String)
data class ImageUploadResponse(val data: ImageUploadFilePath)

// {"error": "<errorCode>"}
data class ImageUploadErrorResponse(val error: String)
