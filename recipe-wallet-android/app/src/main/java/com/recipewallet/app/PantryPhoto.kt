package com.recipewallet.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID

/** Bounded, orientation-corrected JPEGs. Upload happens only when the user chooses Scan. */
internal object PantryPhoto {
    fun cameraFile(context: Context): File {
        val directory = File(context.cacheDir, "pantry-camera").apply { mkdirs() }
        return File.createTempFile("capture-", ".jpg", directory)
    }
    fun cameraUri(context: Context, file: File): Uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.pantry-files", file
    )
    fun removeCapture(context: Context, path: String?) {
        if (path == null) return
        val directory = File(context.cacheDir, "pantry-camera").canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile == directory) file.delete()
    }
    fun read(context: Context, uri: Uri): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { "Choose a supported image, such as a JPEG or PNG." }
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 1400) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Couldn’t open this photo. Please choose another.")
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                ExifInterface(checkNotNull(input)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        }
        return if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    fun save(context: Context, bitmap: Bitmap): String {
        val directory = File(context.cacheDir, "pantry-previews").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
        } catch (error: Exception) { file.delete(); throw error }
        return file.absolutePath
    }
    fun remove(context: Context, path: String?) {
        if (path == null) return
        val directory = File(context.cacheDir, "pantry-previews").canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile == directory) file.delete()
    }
}
