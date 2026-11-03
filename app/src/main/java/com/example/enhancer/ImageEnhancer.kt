package com.example.enhancer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * تحسين الصور: تكبير تدريجي (خطوات 1.5x) ← حدة (Unsharp) ← تدرّج سينمائي ← JPEG.
 * يعمل على أي أبعاد (طولي/عرضي/مربع/بانوراما).
 */
object ImageEnhancer {

    fun run(
        context: Context,
        info: MediaInfo,
        s: EnhanceSettings,
        plan: Plan,
        out: File,
        onProgress: (Int) -> Unit
    ) {
        val cr = context.contentResolver

        // 1) فك الترميز بأقل ذاكرة ممكنة (sample فقط عند التصغير)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(info.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max(plan.outW, plan.outH)) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = cr.openInputStream(info.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("تعذّر فك ترميز الصورة")
        onProgress(5)

        // 2) تصحيح الاتجاه (EXIF)
        MediaLoader.orientationMatrix(MediaLoader.readOrientation(context, info.uri))?.let { m ->
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }

        // 3) تغيير الحجم
        val resized = resize(bmp, plan.outW, plan.outH)
        if (resized !== bmp) bmp.recycle()
        onProgress(20)

        // 4) حدة + تدرّج لوني
        val target = if (resized.isMutable) resized else resized.copy(Bitmap.Config.ARGB_8888, true).also { resized.recycle() }
        process(target, s) { p -> onProgress(20 + (p * 0.7f).roundToInt()) }

        // 5) حفظ
        out.outputStream().use { target.compress(Bitmap.CompressFormat.JPEG, s.quality.jpegQuality, it) }
        target.recycle()
        onProgress(100)
    }

    private fun resize(src: Bitmap, tw: Int, th: Int): Bitmap {
        var cur = src
        // تكبير تدريجي بخطوات صغيرة يعطي حواف أنعم من قفزة واحدة
        while (cur.width * 1.5 < tw) {
            val next = Bitmap.createScaledBitmap(
                cur, (cur.width * 1.5).roundToInt(), (cur.height * 1.5).roundToInt(), true
            )
            if (cur !== src) cur.recycle()
            cur = next
        }
        if (cur.width != tw || cur.height != th) {
            val next = Bitmap.createScaledBitmap(cur, tw, th, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        return cur
    }

    private fun process(bmp: Bitmap, s: EnhanceSettings, onProgress: (Int) -> Unit) {
        val w = bmp.width
        val h = bmp.height
        val src = IntArray(w * h)
        bmp.getPixels(src, 0, w, 0, 0, w, h)
        val dst = IntArray(w * h)

        val amount = s.sharpness * 1.2f
        val grade = s.cinematic
        val k = s.cinematicStrength
        val rgb = FloatArray(3)
        val inv = 1f / 255f

        for (y in 0 until h) {
            val yu = if (y > 0) y - 1 else y
            val yd = if (y < h - 1) y + 1 else y
            for (x in 0 until w) {
                val xl = if (x > 0) x - 1 else x
                val xr = if (x < w - 1) x + 1 else x
                val i = y * w + x
                val c = src[i]
                var r = ((c shr 16) and 255) * inv
                var g = ((c shr 8) and 255) * inv
                var b = (c and 255) * inv

                if (amount > 0f) {
                    val l = src[y * w + xl]; val rt = src[y * w + xr]
                    val u = src[yu * w + x]; val d = src[yd * w + x]
                    val br = (((l shr 16) and 255) + ((rt shr 16) and 255) + ((u shr 16) and 255) + ((d shr 16) and 255)) * 0.25f * inv
                    val bg = (((l shr 8) and 255) + ((rt shr 8) and 255) + ((u shr 8) and 255) + ((d shr 8) and 255)) * 0.25f * inv
                    val bb = ((l and 255) + (rt and 255) + (u and 255) + (d and 255)) * 0.25f * inv
                    r = (r + (r - br) * amount).coerceIn(0f, 1f)
                    g = (g + (g - bg) * amount).coerceIn(0f, 1f)
                    b = (b + (b - bb) * amount).coerceIn(0f, 1f)
                }
                if (grade) {
                    CinematicGrade.apply(r, g, b, k, rgb)
                    r = rgb[0]; g = rgb[1]; b = rgb[2]
                }
                dst[i] = (0xFF shl 24) or
                    ((r * 255f + 0.5f).toInt() shl 16) or
                    ((g * 255f + 0.5f).toInt() shl 8) or
                    (b * 255f + 0.5f).toInt()
            }
            if (y % 64 == 0) onProgress(y * 100 / h)
        }
        bmp.setPixels(dst, 0, w, 0, 0, w, h)
    }
}
