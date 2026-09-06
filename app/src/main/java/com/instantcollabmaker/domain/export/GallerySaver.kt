package com.instantcollabmaker.domain.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Saves bitmaps to the device gallery via MediaStore.
 *
 * API 29+ uses scoped storage (no permission needed); below that, saving requires
 * `WRITE_EXTERNAL_STORAGE`, which is declared (maxSdkVersion 28) in the manifest.
 */
class GallerySaver(private val context: Context) {

    /**
     * Saves a bitmap to the Pictures/Game Of Frames directory.
     *
     * @param bitmap The bitmap to save
     * @param displayName Filename without extension, e.g. "Collage_PersonA"
     * @return URI of the saved image, or null on failure
     */
    suspend fun save(bitmap: Bitmap, displayName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Game Of Frames")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val imageUri = resolver.insert(collection, contentValues) ?: return@withContext null

            resolver.openOutputStream(imageUri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            imageUri
        } catch (e: IOException) {
            null
        }
    }
}
