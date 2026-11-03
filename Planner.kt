package com.example.enhancer

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class Plan(
    val outW: Int,
    val outH: Int,
    val videoBitrate: Int,          // bit/s (للفيديو فقط)
    val estimatedBytes: Long,
    val requiredFreeBytes: Long,
    val notes: List<String>
) {
    val megapixels: Double get() = outW.toDouble() * outH / 1_000_000.0
}

/** يحسب أبعاد الإخراج والحجم المتوقع بناءً على المدة والدقة والجودة. */
object Planner {
    private const val IMAGE_MAX_LONG_SIDE = 8192
    private const val VIDEO_MAX_LONG_SIDE = 4096
    private const val AUDIO_BITRATE = 160_000

    fun plan(info: MediaInfo, s: EnhanceSettings): Plan {
        val notes = mutableListOf<String>()
        var w = info.width.coerceAtLeast(2)
        var h = info.height.coerceAtLeast(2)

        s.resolution.shortSide?.let { target ->
            val sc = target.toDouble() / min(w, h)
            w = (w * sc).roundToInt()
            h = (h * sc).roundToInt()
        }

        val maxLong = if (info.kind == MediaKind.VIDEO) VIDEO_MAX_LONG_SIDE else IMAGE_MAX_LONG_SIDE
        if (max(w, h) > maxLong) {
            val sc = maxLong.toDouble() / max(w, h)
            w = (w * sc).roundToInt()
            h = (h * sc).roundToInt()
            notes += "تم تحديد الأبعاد بحد أقصى $maxLong بكسل للضلع الأطول."
        }
        w = (w and 1.inv()).coerceAtLeast(2)
        h = (h and 1.inv()).coerceAtLeast(2)

        val scale = w.toDouble() / info.width.coerceAtLeast(1)
        if (scale > 2.0) notes += "التكبير الكبير يحسّن الحدة والألوان لكنه لا يصنع تفاصيل جديدة."
        if (scale < 0.99) notes += "الدقة المختارة أقل من الأصل."

        return if (info.kind == MediaKind.VIDEO) planVideo(info, s, w, h, notes)
        else planImage(info, s, w, h, notes)
    }

    private fun planVideo(info: MediaInfo, s: EnhanceSettings, w: Int, h: Int, notes: List<String>): Plan {
        val fps = info.fps.coerceIn(24f, 60f)
        val bpp = if (s.useHevc) 0.045 else 0.075
        val sharpBoost = 1.0 + 0.15 * s.sharpness
        val bitrate = (w.toDouble() * h * fps * bpp * s.quality.bitrateFactor * sharpBoost)
            .coerceIn(1_000_000.0, 100_000_000.0)
            .roundToLong()
        val seconds = info.durationMs / 1000.0
        val bytes = ((bitrate + AUDIO_BITRATE) * seconds / 8.0 * 1.015).roundToLong()
        // أثناء التصدير يوجد ملف مؤقت + نسخة نهائية في المعرض
        return Plan(w, h, bitrate.toInt(), bytes, bytes * 2 + 50_000_000L, notes)
    }

    private fun planImage(info: MediaInfo, s: EnhanceSettings, w: Int, h: Int, notes: List<String>): Plan {
        val px = w.toLong() * h
        val bytes = (px * s.quality.bytesPerPixel * (1.0 + 0.3 * s.sharpness)).roundToLong()
        // الذاكرة المؤقتة للمعالجة تُدار في الـ RAM، والملف المؤقت + النهائي على التخزين
        return Plan(w, h, 0, bytes, bytes * 2 + 20_000_000L, notes)
    }
}
