package com.example.enhancer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max

/** يقرأ معلومات الصورة/الفيديو (الأبعاد، المدة، الحجم) ويجهّز صورة مصغّرة للمعاينة. */
object MediaLoader {

    fun load(ctx: Context, uri: Uri, kind: MediaKind): MediaInfo {
        var name = "media"
        var size = 0L
        ctx.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        return if (kind == MediaKind.VIDEO) video(ctx, uri, name, size) else image(ctx, uri, name, size)
    }

    private fun video(ctx: Context, uri: Uri, name: String, size: Long): MediaInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            require(w > 0 && h > 0) { "invalid video" }

            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frames = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toFloatOrNull()
            val fps = if (frames != null && dur > 0) (frames * 1000f / dur).coerceIn(10f, 120f) else 30f

            val thumb = r.getFrameAtTime(dur * 1000 / 4, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let { scaleDown(it, 960) }
            return MediaInfo(uri, MediaKind.VIDEO, w, h, dur, fps, name, size, thumb)
        } finally {
            r.release()
        }
    }

    private fun image(ctx: Context, uri: Uri, name: String, size: Long): MediaInfo {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var w = bounds.outWidth
        var h = bounds.outHeight
        require(w > 0 && h > 0) { "invalid image" }

        val orientation = readOrientation(ctx, uri)
        if (orientation in intArrayOf(5, 6, 7, 8)) { val t = w; w = h; h = t }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var thumb = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (thumb != null) {
            orientationMatrix(orientation)?.let {
                thumb = Bitmap.createBitmap(thumb!!, 0, 0, thumb!!.width, thumb!!.height, it, true)
            }
        }
        return MediaInfo(uri, MediaKind.IMAGE, w, h, 0L, 0f, name, size, thumb)
    }

    fun readOrientation(ctx: Context, uri: Uri): Int =
        try {
            ctx.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }

    fun orientationMatrix(orientation: Int): Matrix? {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            else -> return null
        }
        return m
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val m = max(src.width, src.height)
        if (m <= maxSide) return src
        val sc = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(src, (src.width * sc).toInt(), (src.height * sc).toInt(), true)
    }
}
