package com.instantcollabmaker.domain.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Creates share intents for bitmaps via FileProvider.
 *
 * Phase 1 exports to cache and shares via ACTION_SEND. The FileProvider authority
 * must be declared in AndroidManifest.xml with a matching file_paths.xml.
 */
class ShareManager(private val context: Context) {

    /**
     * Creates a share intent for a bitmap.
     *
     * @param bitmap The bitmap to share
     * @param fileName Name for the temporary file, e.g. "collage.jpg"
     * @return Share intent, or null on failure
     */
    suspend fun createShareIntent(bitmap: Bitmap, fileName: String): Intent? = withContext(Dispatchers.IO) {
        try {
            val cachePath = File(context.cacheDir, "shared_images")
            cachePath.mkdirs()

            val file = File(cachePath, fileName)
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: IOException) {
            null
        }
    }
}
