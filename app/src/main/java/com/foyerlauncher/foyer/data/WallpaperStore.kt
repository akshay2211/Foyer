package com.foyerlauncher.foyer.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt

/** The installed wallpaper image; missing file means none is set. */
fun wallpaperFile(context: Context): File = File(context.filesDir, "wallpaper/bg.jpg")

/**
 * Imports a user-picked photo as the home wallpaper: downsampled to roughly
 * the screen size on the way in, so the home screen only ever decodes a small
 * file (a 50MP camera photo must never reach the render path).
 */
object WallpaperStore {
    suspend fun install(
        context: Context,
        uri: Uri,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val metrics = context.resources.displayMetrics
                val bitmap = decodeScaled(context.contentResolver, uri, metrics.widthPixels, metrics.heightPixels)
                val dest = wallpaperFile(context)
                dest.parentFile?.mkdirs()
                val part = File(dest.path + ".part")
                part.outputStream().use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
                        throw IOException("couldn't save that photo")
                    }
                }
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) throw IOException("couldn't save that photo")
            }
        }

    fun remove(context: Context) {
        wallpaperFile(context).delete()
    }

    private fun decodeScaled(
        resolver: ContentResolver,
        uri: Uri,
        targetW: Int,
        targetH: Int,
    ): Bitmap {
        // Bounds decode intentionally returns null (inJustDecodeBounds), so
        // the stream and the decode result must be null-checked separately.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: throw IOException("couldn't read that photo")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("that doesn't look like an image")

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decodeStream = resolver.openInputStream(uri) ?: throw IOException("couldn't read that photo")
        val decoded =
            decodeStream.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IOException("that doesn't look like an image")

        val upright = rotateUpright(resolver, uri, decoded)

        // Final cap: EXIF rotation aside, keep the stored file within 1.5x the
        // screen's short side so draw-time decode stays cheap.
        val shortSide = min(upright.width, upright.height)
        val maxShort = (min(targetW, targetH) * 1.5f).roundToInt()
        if (shortSide <= maxShort) return upright
        val scale = maxShort / shortSide.toFloat()
        return Bitmap.createScaledBitmap(
            upright,
            (upright.width * scale).roundToInt().coerceAtLeast(1),
            (upright.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun rotateUpright(
        resolver: ContentResolver,
        uri: Uri,
        bitmap: Bitmap,
    ): Bitmap {
        val degrees =
            runCatching {
                resolver.openInputStream(uri)?.use { stream ->
                    when (
                        ExifInterface(stream).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        )
                    ) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
