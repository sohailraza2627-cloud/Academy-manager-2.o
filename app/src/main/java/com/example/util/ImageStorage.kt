package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageStorage {
    private val memoryCache = object : LruCache<String, Bitmap>(32) {}

    fun encodeUriToBase64Jpeg(
        context: Context,
        uri: Uri,
        maxDim: Int = 1280
    ): Pair<String, Bitmap>? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val sampleSize = calculateInSampleSize(options, maxDim, maxDim)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            base64 to bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun copyUriToInternalStorage(context: Context, uri: Uri, folderName: String): String? {
        return try {
            val dir = File(context.filesDir, folderName)
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "${UUID.randomUUID()}.jpg")

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val sampleSize = calculateInSampleSize(options, 512, 512)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, fos)
            }
            memoryCache.put(outFile.absolutePath, bitmap)
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun loadBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        memoryCache.get(path)?.let { return it }
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(options, 320, 320)
            }
            BitmapFactory.decodeFile(path, decodeOptions)?.also {
                memoryCache.put(path, it)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun deleteFileIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        memoryCache.remove(path)
        runCatching { File(path).delete() }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
